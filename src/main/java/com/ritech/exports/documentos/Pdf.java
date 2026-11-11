package com.ritech.exports.documentos;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Estilos y piezas comunes de los PDF (OpenPDF, fuentes base Helvetica en Cp1252: admite tildes y ñ). */
final class Pdf {

    static final Color VERDE = new Color(0x2E, 0x5E, 0x3A);
    static final Color FONDO = new Color(0xF2, 0xF1, 0xEA);
    static final Color BORDE = new Color(0xC9, 0xC6, 0xB8);

    static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, VERDE);
    static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, VERDE);
    static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    static final Font TABLA = FontFactory.getFont(FontFactory.HELVETICA, 8);
    static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.DARK_GRAY);

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Pdf() {
    }

    @FunctionalInterface
    interface Contenido {
        void escribir(Document d) throws DocumentException;
    }

    static byte[] generar(Rectangle pagina, String pie, Contenido contenido) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document d = new Document(pagina, 36, 36, 36, 48);
        try {
            PdfWriter w = PdfWriter.getInstance(d, out);
            w.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                            new Phrase(pie + "  ·  Página " + writer.getPageNumber(), PEQUENA),
                            (doc.left() + doc.right()) / 2, doc.bottom() - 24, 0);
                }
            });
            d.addTitle(pie);
            d.addCreator("RiTech Exports");
            d.open();
            contenido.escribir(d);
            d.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /** Cabecera: datos del exportador a la izquierda, tipo y numero del documento a la derecha. */
    static PdfPTable encabezado(DatosEmpresa e, String tipoDocumento, String numero, LocalDate fecha) {
        PdfPTable t = new PdfPTable(new float[]{58, 42});
        t.setWidthPercentage(100);
        PdfPCell izq = new PdfPCell();
        izq.setBorder(Rectangle.NO_BORDER);
        izq.addElement(new Paragraph(e.razonSocial(), TITULO));
        StringBuilder sb = new StringBuilder();
        if (!e.nit().isBlank()) {
            sb.append("NIT ").append(e.nit()).append('\n');
        }
        if (!e.direccion().isBlank()) {
            sb.append(e.direccion()).append('\n');
        }
        sb.append(e.ciudad().isBlank() ? e.pais() : e.ciudad() + ", " + e.pais());
        if (!e.email().isBlank()) {
            sb.append('\n').append(e.email());
        }
        izq.addElement(new Paragraph(sb.toString(), NORMAL));
        t.addCell(izq);

        PdfPCell der = new PdfPCell();
        der.setBackgroundColor(FONDO);
        der.setBorderColor(BORDE);
        der.setPadding(8);
        Paragraph tipo = new Paragraph(tipoDocumento, SECCION);
        tipo.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(tipo);
        Paragraph num = new Paragraph("N.º " + numero + "\nFecha: " + fecha(fecha), NEGRITA);
        num.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(num);
        t.addCell(der);
        t.setSpacingAfter(6);
        return t;
    }

    static Paragraph seccion(String texto) {
        Paragraph p = new Paragraph(texto, SECCION);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    /** Tabla de pares etiqueta / valor, en dos columnas de pares. */
    static PdfPTable pares(String... etiquetasYValores) {
        PdfPTable t = new PdfPTable(new float[]{18, 32, 18, 32});
        t.setWidthPercentage(100);
        for (int i = 0; i < etiquetasYValores.length; i += 2) {
            t.addCell(sinBorde(etiquetasYValores[i], NEGRITA));
            t.addCell(sinBorde(texto(etiquetasYValores[i + 1]), NORMAL));
        }
        if ((etiquetasYValores.length / 2) % 2 == 1) {
            t.addCell(sinBorde("", NORMAL));
            t.addCell(sinBorde("", NORMAL));
        }
        return t;
    }

    static PdfPTable tabla(float... anchos) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setHeaderRows(1);
        return t;
    }

    static void cabeceras(PdfPTable t, String... titulos) {
        for (String s : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(s, CABECERA));
            c.setBackgroundColor(VERDE);
            c.setBorderColor(VERDE);
            c.setPadding(4);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(c);
        }
    }

    static PdfPCell celda(String texto, int alineacion) {
        return celda(texto, TABLA, alineacion);
    }

    static PdfPCell celda(String texto, Font f, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto(texto), f));
        c.setBorderColor(BORDE);
        c.setPadding(4);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    static PdfPCell sinBorde(String texto, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(2);
        return c;
    }

    static Paragraph nota(String texto) {
        Paragraph p = new Paragraph(texto, PEQUENA);
        p.setSpacingBefore(8);
        return p;
    }

    static PdfPTable firma(String quien) {
        PdfPTable t = new PdfPTable(new float[]{45, 55});
        t.setWidthPercentage(100);
        t.setSpacingBefore(36);
        PdfPCell c = new PdfPCell(new Phrase(quien, NORMAL));
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(Color.GRAY);
        c.setPaddingTop(4);
        t.addCell(c);
        t.addCell(sinBorde("", NORMAL));
        return t;
    }

    // ---------- Formato ----------

    static String texto(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    static String fecha(LocalDate f) {
        return f == null ? "—" : FECHA.format(f);
    }

    static String numero(BigDecimal n, int decimales) {
        if (n == null) {
            return "—";
        }
        DecimalFormat f = new DecimalFormat(decimales == 0 ? "#,##0" : "#,##0." + "0".repeat(decimales),
                DecimalFormatSymbols.getInstance(ES_CO));
        return f.format(n.setScale(decimales, RoundingMode.HALF_UP));
    }
}
