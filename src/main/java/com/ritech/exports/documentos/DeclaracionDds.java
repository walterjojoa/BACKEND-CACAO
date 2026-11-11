package com.ritech.exports.documentos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * SCRUM-24: declaracion de diligencia debida (DDS) del Reglamento (UE) 2023/1115 para un lote.
 * El PDF resume la declaracion; el GeoJSON es el archivo de geolocalizacion que se carga en TRACES.
 */
final class DeclaracionDds {

    /** Fecha de corte del art. 2.13 del EUDR: sin deforestacion despues del 31/12/2020. */
    static final LocalDate FECHA_CORTE = LocalDate.of(2020, 12, 31);

    record Finca(String nombre, String productor, String documentoProductor, String municipio,
                 String departamento, BigDecimal areaHectareas, BigDecimal latitud, BigDecimal longitud,
                 String poligonoGeojson, LocalDate verificacionDeforestacion, BigDecimal porcentaje,
                 BigDecimal kg) {

        boolean tienePoligono() {
            return poligonoGeojson != null && !poligonoGeojson.isBlank();
        }
    }

    record Importador(String numeroOrden, String razonSocial, String eori, String pais) {
    }

    record Datos(String referencia, String codigoLote, String tipo, String producto, String variedad,
                 String codigoHs, BigDecimal pesoNetoKg, LocalDate fechaCosecha, String estadoLote,
                 String dictamen, Importador importador, List<Finca> fincas) {

        long fincasVerificadas() {
            return fincas.stream().filter(f -> f.verificacionDeforestacion() != null).count();
        }
    }

    private DeclaracionDds() {
    }

    static byte[] pdf(DatosEmpresa e, Datos d, LocalDate emision) {
        return Pdf.generar(PageSize.A4.rotate(), "Declaración de diligencia debida " + d.referencia(), doc -> {
            doc.add(Pdf.encabezado(e, "DECLARACIÓN DE DILIGENCIA DEBIDA (DDS) — EUDR", d.referencia(), emision));
            doc.add(new Paragraph("Reglamento (UE) 2023/1115 relativo a la comercialización en el mercado de la "
                    + "Unión de materias primas asociadas a la deforestación. Anexo II.", Pdf.PEQUENA));

            doc.add(Pdf.seccion("1. Operador / exportador"));
            doc.add(Pdf.pares(
                    "Razón social", e.razonSocial(),
                    "NIT", e.nit(),
                    "Dirección", e.direccion(),
                    "País", e.pais(),
                    "Correo", e.email()));

            doc.add(Pdf.seccion("2. Importador en la Unión Europea"));
            Importador im = d.importador();
            if (im == null) {
                doc.add(Pdf.pares("Estado", "El lote aún no está asignado a una orden de exportación"));
            } else {
                doc.add(Pdf.pares(
                        "Razón social", im.razonSocial(),
                        "EORI", im.eori(),
                        "País", im.pais(),
                        "Orden", im.numeroOrden()));
            }

            doc.add(Pdf.seccion("3. Producto"));
            doc.add(Pdf.pares(
                    "Lote", d.codigoLote(),
                    "Materia prima", "CAFE".equals(d.tipo()) ? "Café" : "CACAO".equals(d.tipo()) ? "Cacao" : d.tipo(),
                    "Descripción", d.variedad() == null || d.variedad().isBlank()
                            ? d.producto() : d.producto() + " (" + d.variedad() + ")",
                    "Código HS", d.codigoHs(),
                    "Cantidad neta", Pdf.numero(d.pesoNetoKg(), 2) + " kg",
                    "País de producción", "Colombia (CO)",
                    "Fecha de cosecha", Pdf.fecha(d.fechaCosecha()),
                    "Estado del lote", d.estadoLote()));

            doc.add(Pdf.seccion("4. Geolocalización de las parcelas de producción (" + d.fincas().size() + ")"));
            PdfPTable t = Pdf.tabla(4, 15, 17, 15, 7, 9, 9, 7, 7, 10);
            Pdf.cabeceras(t, "#", "Finca", "Productor", "Municipio", "Área ha", "Latitud", "Longitud",
                    "Polígono", "Aporte", "Verif. deforest.");
            int i = 1;
            for (Finca f : d.fincas()) {
                t.addCell(Pdf.celda(String.valueOf(i++), Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(f.nombre(), Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(f.productor() + (f.documentoProductor() == null ? "" : "\n" + f.documentoProductor()),
                        Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(f.municipio() + ", " + f.departamento(), Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(Pdf.numero(f.areaHectareas(), 2), Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(f.latitud().toPlainString(), Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(f.longitud().toPlainString(), Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(f.tienePoligono() ? "Sí" : "Punto", Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(Pdf.numero(f.porcentaje(), 2) + " %", Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(Pdf.fecha(f.verificacionDeforestacion()), Element.ALIGN_CENTER));
            }
            doc.add(t);
            doc.add(Pdf.nota("Las coordenadas están en grados decimales WGS84 con seis decimales. Las parcelas de "
                    + "más de 4 ha se declaran con polígono (art. 2.28). El archivo GeoJSON con todas las parcelas se "
                    + "descarga en /api/lotes/{id}/dds/geojson para adjuntarlo en TRACES."));

            doc.add(Pdf.seccion("5. Evaluación del riesgo"));
            doc.add(Pdf.pares(
                    "Dictamen normativo", d.dictamen(),
                    "Trazabilidad", "Completa (100 % del peso neto con finca de origen)",
                    "Fecha de corte", Pdf.fecha(FECHA_CORTE),
                    "Fincas verificadas", d.fincasVerificadas() + " de " + d.fincas().size()));

            doc.add(Pdf.seccion("6. Declaración"));
            doc.add(new Paragraph("Mediante la presentación de esta declaración de diligencia debida, el operador "
                    + "confirma que ha ejercido la diligencia debida de conformidad con el Reglamento (UE) 2023/1115 "
                    + "y que no se ha detectado ningún riesgo, o solo un riesgo insignificante, de que los productos "
                    + "pertinentes no cumplan lo dispuesto en el artículo 3, letras a) o b), de dicho Reglamento.",
                    Pdf.NORMAL));
            doc.add(Pdf.nota("Documento generado por RiTech Exports a partir de la trazabilidad registrada. La "
                    + "declaración oficial se presenta en el Sistema de Información de la UE (TRACES), que asigna el "
                    + "número de referencia y el número de verificación."));
            doc.add(Pdf.firma("Representante legal — " + e.razonSocial()));
        });
    }

    /**
     * FeatureCollection con una parcela por finca: el poligono registrado si existe y, si no, un punto.
     * Coordenadas en orden [longitud, latitud] como exige GeoJSON (RFC 7946).
     */
    static ObjectNode geojson(ObjectMapper json, Datos d) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("type", "FeatureCollection");
        ArrayNode features = raiz.putArray("features");
        for (Finca f : d.fincas()) {
            ObjectNode feature = features.addObject();
            feature.put("type", "Feature");
            ObjectNode props = feature.putObject("properties");
            props.put("ProducerName", f.productor());
            props.put("ProducerCountry", "CO");
            props.put("ProductionPlace", f.nombre());
            props.put("Area", f.areaHectareas());
            props.put("Municipality", f.municipio() + ", " + f.departamento());
            props.put("ContributionPercent", f.porcentaje());
            props.put("Lot", d.codigoLote());
            feature.set("geometry", geometria(json, f));
        }
        return raiz;
    }

    private static JsonNode geometria(ObjectMapper json, Finca f) {
        if (f.tienePoligono()) {
            try {
                JsonNode n = json.readTree(f.poligonoGeojson());
                // Se acepta lo que se haya guardado: geometria, Feature o FeatureCollection.
                if ("FeatureCollection".equals(n.path("type").asText()) && n.path("features").size() > 0) {
                    n = n.path("features").get(0);
                }
                if ("Feature".equals(n.path("type").asText())) {
                    n = n.path("geometry");
                }
                if (n.hasNonNull("type") && n.has("coordinates")) {
                    return n;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Poligono mal formado: se declara el punto de referencia de la finca.
            }
        }
        ObjectNode punto = json.createObjectNode();
        punto.put("type", "Point");
        ArrayNode c = punto.putArray("coordinates");
        c.add(f.longitud());
        c.add(f.latitud());
        return punto;
    }
}
