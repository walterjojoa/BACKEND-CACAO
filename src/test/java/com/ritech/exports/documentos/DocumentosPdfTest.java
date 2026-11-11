package com.ritech.exports.documentos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.ritech.exports.ordenes.OrdenExportacion;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SCRUM-27 y SCRUM-24: el contenido de los PDF y del GeoJSON, sin base de datos. */
class DocumentosPdfTest {

    private static final DatosEmpresa EMPRESA = new DatosEmpresa("RiTech SAS", "900123456-7", null, "Pasto",
            null, "exportaciones@ritech.co");

    private static OrdenExportacion orden() {
        OrdenExportacion o = new OrdenExportacion();
        o.setIdOrden(1);
        o.setNumeroOrden("ORD-2026-0007");
        o.setFechaOrden(LocalDate.of(2026, 10, 2));
        o.setIncoterm("FOB");
        o.setMoneda("EUR");
        o.setEstadoOrden(OrdenExportacion.CONFIRMADA);
        return o;
    }

    private static DocumentosOrden.Datos datosOrden() {
        DocumentosOrden.Cliente c = new DocumentosOrden.Cliente("Europa Coffee GmbH", "DE123456789012345",
                "DE811907980", "Am Sandtorkai 1", "Hamburgo", "Alemania", "compras@europa.example");
        List<DocumentosOrden.Linea> lineas = List.of(
                new DocumentosOrden.Linea("CAF-2026-0001", "CAFE", "Café verde excelso", "Castillo", "0901.11",
                        LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), new BigDecimal("6.5000"),
                        new BigDecimal("2000.00"), new BigDecimal("2050.00")),
                new DocumentosOrden.Linea("CAF-2026-0002", "CAFE", "Café verde supremo", null, "0901.11",
                        LocalDate.of(2026, 8, 9), new BigDecimal("500.50"), new BigDecimal("7.2500"),
                        new BigDecimal("500.50"), new BigDecimal("510.00")));
        DocumentosOrden.Embarque e = new DocumentosOrden.Embarque("Maersk", "NAVIERA", "MSKU1234567", "BK-889",
                "Buenaventura", "Hamburgo", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 15), "EN_PUERTO");
        return new DocumentosOrden.Datos(orden(), c, lineas, e);
    }

    private static String texto(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        PdfReader r = new PdfReader(pdf);
        StringBuilder sb = new StringBuilder();
        PdfTextExtractor ex = new PdfTextExtractor(r);
        for (int i = 1; i <= r.getNumberOfPages(); i++) {
            sb.append(ex.getTextFromPage(i)).append('\n');
        }
        r.close();
        return sb.toString();
    }

    @Test
    void laLineaCalculaSubtotalYPesoBrutoProporcional() {
        DocumentosOrden.Linea l = datosOrden().lineas().get(0);
        assertThat(l.subtotal()).isEqualByComparingTo("6500.00");
        // 1000 de 2000 kg netos -> la mitad de los 2050 kg brutos
        assertThat(l.pesoBruto()).isEqualByComparingTo("1025.00");
        assertThat(datosOrden().totalValor()).isEqualByComparingTo("10128.63"); // 6500 + 500.50 x 7.25
    }

    @Test
    void laFacturaTraeClienteLotesPreciosYTotal() throws IOException {
        String t = texto(DocumentosOrden.factura(EMPRESA, datosOrden(), "FC-2026-0007", LocalDate.of(2026, 10, 3)));

        assertThat(t).contains("FACTURA COMERCIAL", "FC-2026-0007", "03/10/2026", "RiTech SAS", "NIT 900123456-7",
                "Europa Coffee GmbH", "DE123456789012345", "ORD-2026-0007", "FOB",
                "CAF-2026-0001", "CAF-2026-0002", "0901.11", "Café verde excelso (Castillo)",
                "6.500,00", "EUR 10.128,63", "Maersk", "Buenaventura");
    }

    @Test
    void laListaDeEmpaqueTraePesosPeroNoPrecios() throws IOException {
        String t = texto(DocumentosOrden.listaEmpaque(EMPRESA, datosOrden(), "LE-2026-0007", LocalDate.of(2026, 10, 3)));

        assertThat(t).contains("LISTA DE EMPAQUE", "LE-2026-0007", "MSKU1234567", "BK-889",
                "1.500,50", "1.535,00", "2 lote(s)");
        assertThat(t).doesNotContain("6,5000").doesNotContain("10.128,63");
    }

    private static DeclaracionDds.Datos datosDds(String poligono) {
        return new DeclaracionDds.Datos("DDS-CAF-2026-0001", "CAF-2026-0001", "CAFE", "Café verde excelso",
                null, "0901.11", new BigDecimal("2000.00"), LocalDate.of(2026, 8, 1), "ASIGNADO", "CUMPLE",
                new DeclaracionDds.Importador("ORD-2026-0007", "Europa Coffee GmbH", "DE123456789012345", "Alemania"),
                List.of(
                        new DeclaracionDds.Finca("La Esperanza", "Maria Lopez", "27123456", "La Union", "Narino",
                                new BigDecimal("6.50"), new BigDecimal("1.604512"), new BigDecimal("-77.131010"),
                                poligono, LocalDate.of(2025, 3, 1), new BigDecimal("60.00"),
                                new BigDecimal("1200.00")),
                        new DeclaracionDds.Finca("El Mirador", "Jose Rosero", "12987654", "Buesaco", "Narino",
                                new BigDecimal("2.10"), new BigDecimal("1.381200"), new BigDecimal("-77.155300"),
                                null, null, new BigDecimal("40.00"), new BigDecimal("800.00"))));
    }

    @Test
    void laDdsTraeOperadorImportadorProductoYParcelas() throws IOException {
        String t = texto(DeclaracionDds.pdf(EMPRESA, datosDds("{\"type\":\"Polygon\",\"coordinates\":[]}"),
                LocalDate.of(2026, 11, 10)));

        assertThat(t).contains("DDS-CAF-2026-0001", "2023/1115", "RiTech SAS", "Europa Coffee GmbH",
                "DE123456789012345", "0901.11", "2.000,00 kg", "La Esperanza", "El Mirador", "1.604512",
                "-77.131010", "Fincas verificadas", "1 de 2", "31/12/2020", "riesgo insignificante");
    }

    @Test
    void elGeojsonUsaElPoligonoGuardadoYSiNoUnPuntoLonLat() throws IOException {
        ObjectMapper json = new ObjectMapper();
        String feature = """
                {"type":"Feature","properties":{},"geometry":{"type":"Polygon",
                 "coordinates":[[[-77.13,1.60],[-77.12,1.60],[-77.12,1.61],[-77.13,1.60]]]}}""";

        JsonNode g = DeclaracionDds.geojson(json, datosDds(feature));

        assertThat(g.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(g.get("features")).hasSize(2);
        JsonNode conPoligono = g.get("features").get(0);
        assertThat(conPoligono.at("/geometry/type").asText()).isEqualTo("Polygon");
        assertThat(conPoligono.at("/properties/ProducerCountry").asText()).isEqualTo("CO");
        assertThat(conPoligono.at("/properties/ProductionPlace").asText()).isEqualTo("La Esperanza");
        JsonNode punto = g.get("features").get(1).get("geometry");
        assertThat(punto.get("type").asText()).isEqualTo("Point");
        assertThat(punto.at("/coordinates/0").decimalValue()).isEqualByComparingTo("-77.155300"); // longitud primero
        assertThat(punto.at("/coordinates/1").decimalValue()).isEqualByComparingTo("1.381200");
    }

    @Test
    void unPoligonoMalFormadoSeDeclaraComoPunto() {
        JsonNode g = DeclaracionDds.geojson(new ObjectMapper(), datosDds("{no es json"));

        assertThat(g.at("/features/0/geometry/type").asText()).isEqualTo("Point");
    }
}
