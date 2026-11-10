package com.ritech.exports.lotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ritech.exports.Sesion;
import com.ritech.exports.comun.ApiException;
import com.ritech.exports.maestros.FincaRepositorio;
import com.ritech.exports.maestros.Producto;
import com.ritech.exports.maestros.ProductoRepositorio;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-15 registro de lotes y SCRUM-16 fincas de origen. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoteServicioTest {

    @Mock
    LoteRepositorio lotes;
    @Mock
    LoteFincaRepositorio aportes;
    @Mock
    ProductoRepositorio productos;
    @Mock
    FincaRepositorio fincas;
    @Mock
    JdbcClient jdbc;
    @Mock
    EntityManager em;

    private LoteServicio servicio;

    @BeforeEach
    void preparar() {
        servicio = new LoteServicio(lotes, aportes, productos, fincas, jdbc);
        ReflectionTestUtils.setField(servicio, "em", em);
        Producto cafe = new Producto();
        cafe.setIdProducto(1);
        cafe.setTipo("CAFE");
        Producto cacao = new Producto();
        cacao.setIdProducto(3);
        cacao.setTipo("CACAO");
        when(productos.findById(1)).thenReturn(Optional.of(cafe));
        when(productos.findById(3)).thenReturn(Optional.of(cacao));
        when(productos.existsById(1)).thenReturn(true);
        when(lotes.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(aportes.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Sesion.gestor();
    }

    @AfterEach
    void limpiar() {
        Sesion.limpiar();
    }

    private static LoteServicio.LoteSolicitud solicitud(String codigo, int producto, String neto, String bruto) {
        return new LoteServicio.LoteSolicitud(codigo, producto, new BigDecimal(neto), new BigDecimal(bruto),
                LocalDate.now().minusDays(10));
    }

    // ---------- Registro ----------

    @Test
    void generaElCodigoSegunElTipoDeProductoYElAnio() {
        int anio = LocalDate.now().getYear();
        when(lotes.ultimoConsecutivo("CAF-" + anio + "-")).thenReturn(7L);
        when(lotes.ultimoConsecutivo("CAC-" + anio + "-")).thenReturn(0L);

        assertThat(servicio.crear(solicitud(null, 1, "1000", "1020")).getCodigoLote())
                .isEqualTo("CAF-" + anio + "-0008");
        assertThat(servicio.crear(solicitud(" ", 3, "500", "510")).getCodigoLote())
                .isEqualTo("CAC-" + anio + "-0001");
    }

    @Test
    void registraAQuienCreaElLote() {
        LoteExportacion l = servicio.crear(solicitud("CAF-X-1", 1, "1000", "1020"));

        assertThat(l.getIdUsuarioRegistra()).isEqualTo(2);
        verify(em).refresh(l);
    }

    @Test
    void elPesoBrutoNoPuedeSerMenorQueElNeto() {
        assertThatThrownBy(() -> servicio.crear(solicitud(null, 1, "19200", "18000")))
                .hasMessageContaining("peso bruto");
        verify(lotes, never()).saveAndFlush(any());
    }

    @Test
    void codigoDuplicadoEs409() {
        when(lotes.existsByCodigoLote("CAF-2026-0001")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(solicitud("CAF-2026-0001", 1, "100", "110")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).isEqualTo(409);
    }

    @Test
    void soloSeEditaUnLoteRegistrado() {
        when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, "APROBADO", "1000")));

        assertThatThrownBy(() -> servicio.editar(5, solicitud(null, 1, "1000", "1010")))
                .hasMessageContaining("REGISTRADO");
    }

    @Test
    void noSeCambiaElPesoNetoSiYaHayFincasAsociadas() {
        when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, "REGISTRADO", "1000")));
        when(aportes.existsByIdLote(5)).thenReturn(true);

        assertThatThrownBy(() -> servicio.editar(5, solicitud(null, 1, "1500", "1510")))
                .hasMessageContaining("fincas asociadas");
    }

    // ---------- Origen ----------

    @Test
    void calculaLosKgDelAporteSiNoSeEnvian() {
        when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, "REGISTRADO", "19200")));
        when(fincas.existsById(1)).thenReturn(true);

        LoteFinca a = servicio.agregarAporte(5, new LoteServicio.AporteSolicitud(1, new BigDecimal("30"), null));

        ArgumentCaptor<LoteFinca> guardado = ArgumentCaptor.forClass(LoteFinca.class);
        verify(em).persist(guardado.capture());
        assertThat(guardado.getValue().getKgAportados()).isEqualByComparingTo("5760.00");
        assertThat(a.getIdLote()).isEqualTo(5);
    }

    @Test
    void respetaLosKgIndicadosParaQueLosRevaleElTrigger() {
        when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, "REGISTRADO", "10000")));
        when(fincas.existsById(1)).thenReturn(true);

        LoteFinca a = servicio.agregarAporte(5, new LoteServicio.AporteSolicitud(1, new BigDecimal("50"),
                new BigDecimal("12000")));

        assertThat(a.getKgAportados()).isEqualByComparingTo("12000");
    }

    @Test
    void elOrigenSeCongelaCuandoElLoteFueAprobado() {
        for (String estado : List.of("APROBADO", "ASIGNADO", "EXPORTADO")) {
            when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, estado, "1000")));
            assertThatThrownBy(() -> servicio.agregarAporte(5, new LoteServicio.AporteSolicitud(1, BigDecimal.TEN, null)))
                    .as(estado).hasMessageContaining("ya no se puede modificar");
        }
    }

    @Test
    void laFincaDebeExistir() {
        when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, "REGISTRADO", "1000")));
        when(fincas.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> servicio.agregarAporte(5, new LoteServicio.AporteSolicitud(99, BigDecimal.TEN, null)))
                .hasMessageContaining("finca no existe");
    }

    // ---------- Visibilidad del cliente ----------

    @Test
    void unClienteNoVeLotesQueNoCompro() {
        Sesion.cliente(1);
        when(lotes.findById(5)).thenReturn(Optional.of(com.ritech.exports.Sesion.lote(5, "ASIGNADO", "1000")));
        when(lotes.compradoPor(5, 1)).thenReturn(0L);

        assertThatThrownBy(() -> servicio.obtener(5))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).as("404, no 403").isEqualTo(404);
    }

    @Test
    void unClienteVeLosLotesDeSusOrdenes() {
        Sesion.cliente(1);
        LoteExportacion propio = com.ritech.exports.Sesion.lote(5, "ASIGNADO", "1000");
        when(lotes.findById(5)).thenReturn(Optional.of(propio));
        when(lotes.compradoPor(5, 1)).thenReturn(1L);
        when(lotes.delCliente(1)).thenReturn(List.of(propio));

        assertThat(servicio.obtener(5)).isSameAs(propio);
        assertThat(servicio.buscar("REGISTRADO", null)).as("el filtro del cliente ignora el estado")
                .containsExactly(propio);
        verify(lotes, never()).buscar(any(), any());
    }
}
