package com.ritech.exports.maestros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritech.exports.comun.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SCRUM-13 (productos) y SCRUM-14 (productores y fincas): reglas que el backend aplica antes de la base. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaestrosServicioTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    ProductoRepositorio productos;
    @Mock
    ProductorRepositorio productores;
    @Mock
    FincaRepositorio fincas;
    @InjectMocks
    MaestrosServicio servicio;

    @BeforeEach
    void guardarDevuelveLaEntidad() {
        when(productos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(productores.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(fincas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(productores.findById(1)).thenReturn(Optional.of(productor("900123456")));
    }

    private static Productor productor(String nit) {
        Productor p = new Productor();
        p.setIdProductor(1);
        p.setNitOCedula(nit);
        p.setNombre("Asociacion Cafetera del Sur");
        return p;
    }

    private static JsonNode poligono() throws Exception {
        return JSON.readTree("""
                {"type":"Polygon","coordinates":[[[-77.28,1.21],[-77.27,1.21],[-77.27,1.22],[-77.28,1.21]]]}""");
    }

    private static MaestrosServicio.FincaSolicitud finca(String hectareas, JsonNode poligono) {
        return new MaestrosServicio.FincaSolicitud("  La Esperanza ", new BigDecimal(hectareas),
                new BigDecimal("1.2136"), new BigDecimal("-77.2811"), poligono, LocalDate.of(2026, 1, 15));
    }

    // ---------- Productos ----------

    @Test
    void productoSinUnidadQuedaEnKgYVariedadVaciaEnNulo() {
        Producto p = servicio.guardarProducto(null,
                new MaestrosServicio.ProductoSolicitud("CAFE", "  Cafe Supremo Narino ", " ", "090111", null));

        assertThat(p.getNombreComercial()).isEqualTo("Cafe Supremo Narino");
        assertThat(p.getVariedad()).isNull();
        assertThat(p.getUnidadMedida()).isEqualTo("KG");
    }

    @Test
    void editarUnProductoQueNoExisteDa404() {
        when(productos.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.guardarProducto(99,
                new MaestrosServicio.ProductoSolicitud("CACAO", "Cacao fino", null, "1801", "KG")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).isEqualTo(404);
    }

    // ---------- Productores ----------

    @Test
    void rechazaUnNitDeOtroProductor() {
        when(productores.existsByNitOCedula("800555111")).thenReturn(true);

        assertThatThrownBy(() -> servicio.guardarProductor(null,
                new MaestrosServicio.ProductorSolicitud(" 800555111 ", "Finca Nueva", "Pasto", "Narino", null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).isEqualTo(409);
        verify(productores, never()).saveAndFlush(any());
    }

    @Test
    void editarSinCambiarElNitNoLoTomaComoDuplicado() {
        when(productores.existsByNitOCedula("900123456")).thenReturn(true);

        Productor p = servicio.guardarProductor(1,
                new MaestrosServicio.ProductorSolicitud("900123456", " Asociacion Cafetera ", "Buesaco", "Narino", ""));

        assertThat(p.getNombre()).isEqualTo("Asociacion Cafetera");
        assertThat(p.getTelefono()).isNull();
    }

    // ---------- Fincas (EUDR) ----------

    @Test
    void fincaDeMasDeCuatroHectareasSinPoligonoSeRechaza() {
        assertThatThrownBy(() -> servicio.crearFinca(1, finca("4.5", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("4 hectareas");
        verify(fincas, never()).saveAndFlush(any());
    }

    @Test
    void fincaDeExactamenteCuatroHectareasNoExigePoligono() {
        Finca f = servicio.crearFinca(1, finca("4", null));

        assertThat(f.getIdProductor()).isEqualTo(1);
        assertThat(f.getNombreFinca()).isEqualTo("La Esperanza");
        assertThat(f.getPoligonoGeojson()).isNull();
    }

    @Test
    void fincaGrandeConPoligonoGuardaElGeoJson() throws Exception {
        Finca f = servicio.crearFinca(1, finca("12.3", poligono()));

        assertThat(f.getPoligonoGeojson()).contains("\"type\":\"Polygon\"");
    }

    @Test
    void poligonoSinCoordinatesNoEsGeoJsonValido() throws Exception {
        JsonNode malo = JSON.readTree("{\"type\":\"Polygon\"}");

        assertThatThrownBy(() -> servicio.crearFinca(1, finca("2", malo)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("GeoJSON");
    }

    @Test
    void crearFincaParaUnProductorInexistenteDa404() {
        when(productores.findById(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crearFinca(7, finca("1", null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).isEqualTo(404);
    }
}
