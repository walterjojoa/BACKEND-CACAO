package com.ritech.exports.documentos;

import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfPTable;
import com.ritech.exports.ordenes.OrdenExportacion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** SCRUM-27: dibuja la factura comercial y la lista de empaque de una orden. Sin acceso a datos. */
final class DocumentosOrden {

    record Cliente(String razonSocial, String eori, String vat, String direccion, String ciudad, String pais,
                   String email) {
    }

    record Linea(String codigoLote, String tipo, String producto, String variedad, String codigoHs,
                 LocalDate fechaCosecha, BigDecimal kg, BigDecimal precioUnitario, BigDecimal pesoNetoLote,
                 BigDecimal pesoBrutoLote) {

        BigDecimal subtotal() {
            return kg.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP);
        }

        /** Peso bruto proporcional a los kg asignados (el lote puede repartirse). */
        BigDecimal pesoBruto() {
            if (pesoNetoLote == null || pesoNetoLote.signum() == 0 || pesoBrutoLote == null) {
                return kg;
            }
            return pesoBrutoLote.multiply(kg).divide(pesoNetoLote, 2, RoundingMode.HALF_UP);
        }

        String descripcion() {
            String base = ("CAFE".equals(tipo) ? "Café" : "CACAO".equals(tipo) ? "Cacao" : tipo) + " — " + producto;
            return variedad == null || variedad.isBlank() ? base : base + " (" + variedad + ")";
        }
    }

    record Embarque(String transportista, String tipoTransporte, String contenedor, String booking,
                    String puertoOrigen, String puertoDestino, LocalDate zarpe, LocalDate arribo, String estado) {
    }

    record Datos(OrdenExportacion orden, Cliente cliente, List<Linea> lineas, Embarque embarque) {

        BigDecimal totalKg() {
            return lineas.stream().map(Linea::kg).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal totalBruto() {
            return lineas.stream().map(Linea::pesoBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal totalValor() {
            return lineas.stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private DocumentosOrden() {
    }

    static byte[] factura(DatosEmpresa e, Datos d, String numero, LocalDate emision) {
        OrdenExportacion o = d.orden();
        String moneda = o.getMoneda();
        return Pdf.generar(PageSize.A4, "Factura comercial " + numero, doc -> {
            doc.add(Pdf.encabezado(e, "FACTURA COMERCIAL", numero, emision));

            doc.add(Pdf.seccion("Cliente (importador en la UE)"));
            doc.add(datosCliente(d.cliente()));

            doc.add(Pdf.seccion("Condiciones de la venta"));
            doc.add(Pdf.pares(
                    "Orden", o.getNumeroOrden(),
                    "Fecha de la orden", Pdf.fecha(o.getFechaOrden()),
                    "Incoterm 2020", o.getIncoterm(),
                    "Moneda", moneda,
                    "País de origen", e.pais(),
                    "Destino", d.embarque() == null ? d.cliente().pais() : d.embarque().puertoDestino()));

            doc.add(Pdf.seccion("Detalle"));
            PdfPTable t = Pdf.tabla(4, 14, 32, 10, 12, 13, 15);
            Pdf.cabeceras(t, "#", "Lote", "Descripción", "Código HS", "Kg netos",
                    "Precio " + moneda + "/kg", "Subtotal " + moneda);
            int i = 1;
            for (Linea l : d.lineas()) {
                t.addCell(Pdf.celda(String.valueOf(i++), Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(l.codigoLote(), Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(l.descripcion(), Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(l.codigoHs(), Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(Pdf.numero(l.kg(), 2), Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(Pdf.numero(l.precioUnitario(), 4), Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(Pdf.numero(l.subtotal(), 2), Element.ALIGN_RIGHT));
            }
            t.addCell(totalEtiqueta("Total", 4));
            t.addCell(Pdf.celda(Pdf.numero(d.totalKg(), 2), Pdf.NEGRITA, Element.ALIGN_RIGHT));
            t.addCell(Pdf.celda("", Element.ALIGN_RIGHT));
            t.addCell(Pdf.celda(moneda + " " + Pdf.numero(d.totalValor(), 2), Pdf.NEGRITA, Element.ALIGN_RIGHT));
            doc.add(t);

            if (d.embarque() != null) {
                doc.add(Pdf.seccion("Embarque"));
                doc.add(datosEmbarque(d.embarque()));
            }

            doc.add(Pdf.nota("Mercancía de origen colombiano con trazabilidad hasta la finca de origen conforme al "
                    + "Reglamento (UE) 2023/1115 (EUDR). Los valores se expresan en " + moneda + " bajo el Incoterm "
                    + o.getIncoterm() + "."));
            doc.add(Pdf.firma("Firma autorizada — " + e.razonSocial()));
        });
    }

    static byte[] listaEmpaque(DatosEmpresa e, Datos d, String numero, LocalDate emision) {
        OrdenExportacion o = d.orden();
        return Pdf.generar(PageSize.A4, "Lista de empaque " + numero, doc -> {
            doc.add(Pdf.encabezado(e, "LISTA DE EMPAQUE", numero, emision));

            doc.add(Pdf.seccion("Consignatario"));
            doc.add(datosCliente(d.cliente()));

            doc.add(Pdf.seccion("Embarque"));
            if (d.embarque() != null) {
                doc.add(datosEmbarque(d.embarque()));
            } else {
                doc.add(Pdf.pares("Orden", o.getNumeroOrden(), "Incoterm", o.getIncoterm(),
                        "Transporte", "Pendiente de programar"));
            }

            doc.add(Pdf.seccion("Contenido"));
            PdfPTable t = Pdf.tabla(4, 15, 33, 11, 12, 12, 13);
            Pdf.cabeceras(t, "#", "Lote", "Producto", "Código HS", "Cosecha", "Peso neto kg", "Peso bruto kg");
            int i = 1;
            for (Linea l : d.lineas()) {
                t.addCell(Pdf.celda(String.valueOf(i++), Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(l.codigoLote(), Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(l.descripcion(), Element.ALIGN_LEFT));
                t.addCell(Pdf.celda(l.codigoHs(), Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(Pdf.fecha(l.fechaCosecha()), Element.ALIGN_CENTER));
                t.addCell(Pdf.celda(Pdf.numero(l.kg(), 2), Element.ALIGN_RIGHT));
                t.addCell(Pdf.celda(Pdf.numero(l.pesoBruto(), 2), Element.ALIGN_RIGHT));
            }
            t.addCell(totalEtiqueta("Total: " + d.lineas().size() + " lote(s)", 5));
            t.addCell(Pdf.celda(Pdf.numero(d.totalKg(), 2), Pdf.NEGRITA, Element.ALIGN_RIGHT));
            t.addCell(Pdf.celda(Pdf.numero(d.totalBruto(), 2), Pdf.NEGRITA, Element.ALIGN_RIGHT));
            doc.add(t);

            doc.add(Pdf.nota("Orden " + o.getNumeroOrden() + ". El peso bruto de cada línea es proporcional a los kg "
                    + "asignados del lote. Cada lote conserva su código para la trazabilidad EUDR."));
            doc.add(Pdf.firma("Responsable de despacho — " + e.razonSocial()));
        });
    }

    private static PdfPTable datosCliente(Cliente c) {
        return Pdf.pares(
                "Razón social", c.razonSocial(),
                "EORI", c.eori(),
                "VAT", c.vat(),
                "País", c.pais(),
                "Dirección", c.direccion(),
                "Ciudad", c.ciudad(),
                "Correo", c.email());
    }

    private static PdfPTable datosEmbarque(Embarque m) {
        return Pdf.pares(
                "Transportista", m.transportista() + (m.tipoTransporte() == null ? "" : " (" + m.tipoTransporte() + ")"),
                "Estado", m.estado(),
                "Contenedor", m.contenedor(),
                "Booking / BL", m.booking(),
                "Puerto de origen", m.puertoOrigen(),
                "Puerto de destino", m.puertoDestino(),
                "Zarpe estimado", Pdf.fecha(m.zarpe()),
                "Arribo estimado", Pdf.fecha(m.arribo()));
    }

    private static com.lowagie.text.pdf.PdfPCell totalEtiqueta(String texto, int columnas) {
        com.lowagie.text.pdf.PdfPCell c = Pdf.celda(texto, Pdf.NEGRITA, Element.ALIGN_RIGHT);
        c.setColspan(columnas);
        c.setBackgroundColor(Pdf.FONDO);
        return c;
    }
}
