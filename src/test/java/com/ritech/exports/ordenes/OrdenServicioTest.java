package com.ritech.exports.ordenes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ritech.exports.Sesion;
import com.ritech.exports.comercial.ClienteRepositorio;
import com.ritech.exports.comercial.ClienteUe;
import com.ritech.exports.comun.ApiException;
import com.ritech.exports.cumplimiento.ValidacionServicio;
import com.ritech.exports.lotes.LoteServicio;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-25 ordenes y SCRUM-26 asignacion de lotes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdenServicioTest {

    @Mock
    OrdenRepositorio ordenes;
    @Mock
    OrdenLoteRepositorio asignaciones;
    @Mock
    ClienteRepositorio clientes;
    @Mock
    LoteServicio lotes;
    @Mock
    ValidacionServicio validacion;
    @Mock
    EntityManager em;

    private OrdenServicio servicio;

    @BeforeEach
    void preparar() {
        servicio = new OrdenServicio(ordenes, asignaciones, clientes, lotes, validacion);
        ReflectionTestUtils.setField(servicio, "em", em);
        ClienteUe activo = new ClienteUe();
        activo.setIdCliente(1);
        activo.setEstado("ACTIVO");
        ClienteUe inactivo = new ClienteUe();
        inactivo.setIdCliente(2);
        inactivo.setEstado("INACTIVO");
        when(clientes.findById(1)).thenReturn(Optional.of(activo));
        when(clientes.findById(2)).thenReturn(Optional.of(inactivo));
        when(clientes.existsById(1)).thenReturn(true);
        when(ordenes.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Sesion.gestor();
    }

    @AfterEach
    void limpiar() {
        Sesion.limpiar();
    }

    private OrdenExportacion existente(String estado) {
        OrdenExportacion o = Sesion.orden(10, 1, estado);
        when(ordenes.findById(10)).thenReturn(Optional.of(o));
        return o;
    }

    private static ValidacionServicio.Dictamen dictamen(String resultado, int incumplidas) {
        return new ValidacionServicio.Dictamen(5, "CAF-2026-0005", "APROBADO", null, 6, incumplidas, resultado, List.of());
    }

    // ---------- Crear ----------

    @Test
    void creaLaOrdenEnBorradorConNumeroConsecutivoYMonedaEur() {
        String prefijo = "ORD-" + java.time.LocalDate.now().getYear() + "-";
        when(ordenes.ultimoConsecutivo(prefijo)).thenReturn(41L);

        OrdenExportacion o = servicio.crear(new OrdenServicio.OrdenSolicitud(1, null, "FOB", null));

        assertThat(o.getNumeroOrden()).isEqualTo(prefijo + "0042");
        assertThat(o.getEstadoOrden()).isEqualTo("BORRADOR");
        assertThat(o.getMoneda()).isEqualTo("EUR");
        assertThat(o.getIdUsuarioGestor()).isEqualTo(2);
    }

    @Test
    void noSeCreaUnaOrdenParaUnClienteInactivo() {
        assertThatThrownBy(() -> servicio.crear(new OrdenServicio.OrdenSolicitud(2, null, "CIF", "USD")))
                .hasMessageContaining("cliente inactivo");
    }

    // ---------- Estados ----------

    @Test
    void noSeConfirmaUnaOrdenSinLotes() {
        existente("BORRADOR");
        when(asignaciones.findByIdOrden(10)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.cambiarEstado(10, "CONFIRMADA")).hasMessageContaining("sin lotes");
    }

    @Test
    void rechazaSaltosDeEstadoNoPermitidos() {
        existente("BORRADOR");
        assertThatThrownBy(() -> servicio.cambiarEstado(10, "ENTREGADA")).hasMessageContaining("BORRADOR a ENTREGADA");

        existente("ENTREGADA");
        assertThatThrownBy(() -> servicio.cambiarEstado(10, "ANULADA")).hasMessageContaining("ENTREGADA a ANULADA");
    }

    @Test
    void anularBorraLasAsignacionesParaQueElTriggerLibereLosLotes() {
        existente("CONFIRMADA");
        List<OrdenLote> asignadas = List.of(new OrdenLote(), new OrdenLote());
        when(asignaciones.findByIdOrden(10)).thenReturn(asignadas);

        OrdenExportacion o = servicio.cambiarEstado(10, "anulada");

        verify(asignaciones).deleteAll(asignadas);
        assertThat(o.getEstadoOrden()).isEqualTo("ANULADA");
        verify(ordenes, never()).marcarLotesExportados(any());
    }

    @Test
    void alPasarAEnTransitoLosLotesQuedanExportados() {
        existente("CONFIRMADA");

        servicio.cambiarEstado(10, "EN_TRANSITO");

        verify(ordenes).marcarLotesExportados(10);
    }

    // ---------- Asignar lotes ----------

    @Test
    void soloSeAsignanLotesAprobados() {
        existente("BORRADOR");
        when(lotes.obtener(5)).thenReturn(Sesion.lote(5, "EN_INSPECCION", "1000"));

        assertThatThrownBy(() -> servicio.asignar(10, new OrdenServicio.AsignacionSolicitud(5, null, BigDecimal.ONE)))
                .hasMessageContaining("APROBADO");
        verify(validacion, never()).validar(any());
    }

    @Test
    void revalidaElLoteJustoAntesDeAsignarYFrenaElNoCumple() {
        existente("BORRADOR");
        when(lotes.obtener(5)).thenReturn(Sesion.lote(5, "APROBADO", "1000"));
        when(validacion.validar(5)).thenReturn(dictamen("NO_CUMPLE", 1));

        assertThatThrownBy(() -> servicio.asignar(10, new OrdenServicio.AsignacionSolicitud(5, null, BigDecimal.ONE)))
                .hasMessageContaining("NO_CUMPLE").hasMessageContaining("1 regla");
        verify(em, never()).persist(any());
    }

    @Test
    void sinKgIndicadosSeAsignaTodoElPesoNeto() {
        existente("BORRADOR");
        when(lotes.obtener(5)).thenReturn(Sesion.lote(5, "APROBADO", "19200"));
        when(validacion.validar(5)).thenReturn(dictamen("CUMPLE", 0));

        servicio.asignar(10, new OrdenServicio.AsignacionSolicitud(5, null, new BigDecimal("4.85")));

        ArgumentCaptor<OrdenLote> a = ArgumentCaptor.forClass(OrdenLote.class);
        verify(em).persist(a.capture());
        assertThat(a.getValue().getKgAsignados()).isEqualByComparingTo("19200");
        assertThat(a.getValue().getPrecioUnitario()).isEqualByComparingTo("4.85");
    }

    @Test
    void unaOrdenConfirmadaYaNoAdmiteCambiosDeLotes() {
        existente("CONFIRMADA");

        assertThatThrownBy(() -> servicio.asignar(10, new OrdenServicio.AsignacionSolicitud(5, null, BigDecimal.ONE)))
                .hasMessageContaining("BORRADOR");
        assertThatThrownBy(() -> servicio.quitar(10, 5)).hasMessageContaining("BORRADOR");
    }

    // ---------- Visibilidad del cliente ----------

    @Test
    void unClienteSoloVeSusOrdenes() {
        Sesion.cliente(7);
        existente("CONFIRMADA"); // pertenece al cliente 1

        assertThatThrownBy(() -> servicio.obtener(10))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).isEqualTo(404);

        servicio.buscar(1, null);
        verify(ordenes).buscar(7, null); // el filtro por cliente lo impone el backend
    }
}
