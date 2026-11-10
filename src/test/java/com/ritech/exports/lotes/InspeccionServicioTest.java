package com.ritech.exports.lotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ritech.exports.Sesion;
import com.ritech.exports.usuarios.Rol;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-17 inspeccion de calidad y SCRUM-18 aprobar o rechazar con observacion obligatoria. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspeccionServicioTest {

    @Mock
    LoteServicio lotes;
    @Mock
    InspeccionRepositorio inspecciones;
    @Mock
    EntityManager em;

    private InspeccionServicio servicio;
    private LoteExportacion lote;

    @BeforeEach
    void preparar() {
        servicio = new InspeccionServicio(lotes, inspecciones);
        ReflectionTestUtils.setField(servicio, "em", em);
        lote = Sesion.lote(5, "REGISTRADO", "19200");
        when(lotes.obtener(5)).thenReturn(lote);
        when(inspecciones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Sesion.como(Rol.INSPECTOR, 3, null, "INSPECCION_REGISTRAR", "LOTES_CONSULTAR");
    }

    @AfterEach
    void limpiar() {
        Sesion.limpiar();
    }

    private static InspeccionServicio.InspeccionSolicitud inspeccion(String resultado, String observaciones) {
        return new InspeccionServicio.InspeccionSolicitud(null, new BigDecimal("11.2"), 5, new BigDecimal("84.5"),
                new BigDecimal("2.1"), null, resultado, observaciones);
    }

    @Test
    void devuelveElEstadoQueDejoElTriggerEnElLote() {
        // Simula trg_insp_estado_ai: al refrescar, el lote ya viene APROBADO.
        doAnswer(i -> {
            ReflectionTestUtils.setField(lote, "estadoLote", "APROBADO");
            return null;
        }).when(em).refresh(lote);

        InspeccionServicio.InspeccionRegistrada r = servicio.registrar(5, inspeccion("APROBADO", null));

        assertThat(r.estadoLote()).isEqualTo("APROBADO");
        assertThat(r.inspeccion().getIdUsuarioInspector()).as("el inspector es quien inicia sesion").isEqualTo(3);
    }

    @Test
    void rechazarExigeObservacionDeAlMenos20Caracteres() {
        assertThatThrownBy(() -> servicio.registrar(5, inspeccion("RECHAZADO", null)))
                .hasMessageContaining("observacion es obligatoria");
        assertThatThrownBy(() -> servicio.registrar(5, inspeccion("RECHAZADO", "   humedad alta    ")))
                .as("los espacios no cuentan").hasMessageContaining("minimo 20");
        verify(inspecciones, never()).saveAndFlush(any());
    }

    @Test
    void rechazarConObservacionSuficienteSeRegistra() {
        servicio.registrar(5, inspeccion("RECHAZADO", "Humedad de 14% por encima del maximo admitido."));

        verify(inspecciones).saveAndFlush(any());
    }

    @Test
    void unaObservacionEnBlancoSeGuardaComoNula() {
        InspeccionServicio.InspeccionRegistrada r = servicio.registrar(5, inspeccion("APROBADO", "   "));

        assertThat(r.inspeccion().getObservaciones()).isNull();
    }

    @Test
    void noSeInspeccionaUnLoteYaAprobadoAsignadoOExportado() {
        for (String estado : new String[] {"APROBADO", "ASIGNADO", "EXPORTADO"}) {
            ReflectionTestUtils.setField(lote, "estadoLote", estado);
            assertThatThrownBy(() -> servicio.registrar(5, inspeccion("APROBADO", null)))
                    .as(estado).hasMessageContaining("No se puede inspeccionar");
        }
    }

    @Test
    void unLoteRechazadoSePuedeReinspeccionar() {
        ReflectionTestUtils.setField(lote, "estadoLote", "RECHAZADO");

        servicio.registrar(5, inspeccion("APROBADO", null));

        verify(inspecciones).saveAndFlush(any());
    }
}
