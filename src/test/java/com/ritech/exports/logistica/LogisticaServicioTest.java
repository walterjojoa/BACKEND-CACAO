package com.ritech.exports.logistica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ritech.exports.Sesion;
import com.ritech.exports.ordenes.OrdenExportacion;
import com.ritech.exports.ordenes.OrdenServicio;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SCRUM-28 programar envio, SCRUM-29 seguimiento. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogisticaServicioTest {

    @Mock
    TransportistaRepositorio transportistas;
    @Mock
    EnvioRepositorio envios;
    @Mock
    SeguimientoRepositorio seguimiento;
    @Mock
    OrdenServicio ordenes;
    @InjectMocks
    LogisticaServicio servicio;

    private Envio envio;

    @BeforeEach
    void preparar() {
        when(transportistas.existsById(1)).thenReturn(true);
        when(envios.saveAndFlush(any())).thenAnswer(i -> {
            Envio e = i.getArgument(0);
            if (e.getIdEnvio() == null) {
                e.setIdEnvio(20);
            }
            envio = e;
            when(envios.findById(20)).thenReturn(Optional.of(e));
            return e;
        });
        when(seguimiento.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(seguimiento.findByIdEnvioOrderByFechaEventoAscIdSeguimientoAsc(20)).thenReturn(List.of());
        Sesion.gestor();
    }

    @AfterEach
    void limpiar() {
        Sesion.limpiar();
    }

    private static LogisticaServicio.EnvioSolicitud solicitud(String bl, LocalDate zarpe, LocalDate arribo) {
        return new LogisticaServicio.EnvioSolicitud(1, "MSKU1234567", bl, "Buenaventura", "Hamburgo", zarpe, arribo);
    }

    private void ordenEn(String estado) {
        when(ordenes.obtener(10)).thenReturn(Sesion.orden(10, 1, estado));
    }

    @Test
    void programaElEnvioYDejaElPrimerEventoEnLaBitacora() {
        ordenEn("CONFIRMADA");

        LogisticaServicio.EnvioDetalle d = servicio.programar(10,
                solicitud("BK-1", LocalDate.now().plusDays(3), LocalDate.now().plusDays(25)));

        assertThat(d.envio().getEstadoEnvio()).isEqualTo("PROGRAMADO");
        verify(seguimiento).saveAndFlush(any());
    }

    @Test
    void soloSeProgramaElEnvioDeUnaOrdenConfirmada() {
        ordenEn("BORRADOR");

        assertThatThrownBy(() -> servicio.programar(10, solicitud(null, null, null)))
                .hasMessageContaining("CONFIRMADA");
    }

    @Test
    void elArriboNoPuedeSerAnteriorAlZarpe() {
        ordenEn("CONFIRMADA");

        assertThatThrownBy(() -> servicio.programar(10,
                solicitud(null, LocalDate.now().plusDays(10), LocalDate.now().plusDays(2))))
                .hasMessageContaining("anterior al zarpe");
    }

    @Test
    void elBookingBlEsUnico() { // CP-036
        ordenEn("CONFIRMADA");
        when(envios.existsByBookingBl("BK-2026-55012")).thenReturn(true);

        assertThatThrownBy(() -> servicio.programar(10, solicitud("BK-2026-55012", null, null)))
                .hasMessageContaining("BK-2026-55012");
    }

    @Test
    void unEventoEnTransitoAvanzaLaOrden() {
        ordenEn("CONFIRMADA");
        servicio.programar(10, solicitud(null, null, null));

        servicio.registrarEvento(20, new LogisticaServicio.EventoSolicitud(null, "Oceano Atlantico", "EN_TRANSITO", null));

        assertThat(envio.getEstadoEnvio()).isEqualTo("EN_TRANSITO");
        verify(ordenes).cambiarEstado(10, OrdenExportacion.EN_TRANSITO);
    }

    @Test
    void entregadoDesdeConfirmadaRecorreLaCadenaCompleta() {
        ordenEn("CONFIRMADA");
        servicio.programar(10, solicitud(null, null, null));
        when(ordenes.obtener(10)).thenReturn(Sesion.orden(10, 1, "CONFIRMADA"), Sesion.orden(10, 1, "EN_TRANSITO"));

        servicio.registrarEvento(20, new LogisticaServicio.EventoSolicitud(null, "Hamburgo", "ENTREGADO", null));

        InOrder orden = inOrder(ordenes);
        orden.verify(ordenes).cambiarEstado(10, OrdenExportacion.EN_TRANSITO);
        orden.verify(ordenes).cambiarEstado(10, OrdenExportacion.ENTREGADA);
    }

    @Test
    void unaIncidenciaNoTocaLaOrden() {
        ordenEn("CONFIRMADA");
        servicio.programar(10, solicitud(null, null, null));

        servicio.registrarEvento(20, new LogisticaServicio.EventoSolicitud(null, "Puerto", "INCIDENCIA", "Demora aduana"));

        verify(ordenes, never()).cambiarEstado(any(), any());
    }

    @Test
    void unEnvioEntregadoNoAdmiteMasEventos() {
        ordenEn("CONFIRMADA");
        servicio.programar(10, solicitud(null, null, null));
        envio.setEstadoEnvio("ENTREGADO");

        assertThatThrownBy(() -> servicio.registrarEvento(20,
                new LogisticaServicio.EventoSolicitud(null, "Hamburgo", "ARRIBADO", null)))
                .hasMessageContaining("ya fue entregado");
    }
}
