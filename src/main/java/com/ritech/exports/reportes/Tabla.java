package com.ritech.exports.reportes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Resultado tabular de un reporte y su escritura en CSV (RFC 4180, UTF-8 con BOM) o Excel (.xlsx). */
public record Tabla(String nombre, List<String> columnas, List<List<Object>> filas) {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] csv() {
        StringBuilder sb = new StringBuilder("﻿"); // BOM: Excel abre bien las tildes
        sb.append(String.join(",", columnas.stream().map(Tabla::campo).toList())).append("\r\n");
        for (List<Object> fila : filas) {
            sb.append(String.join(",", fila.stream().map(Tabla::texto).map(Tabla::campo).toList())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx() {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(nombre);

            Font negrita = libro.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            CellStyle cabecera = libro.createCellStyle();
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle fechaHora = libro.createCellStyle();
            fechaHora.setDataFormat(libro.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            CellStyle decimal = libro.createCellStyle();
            decimal.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            int[] ancho = new int[columnas.size()];
            Row r = hoja.createRow(0);
            for (int c = 0; c < columnas.size(); c++) {
                Cell celda = r.createCell(c);
                celda.setCellValue(columnas.get(c));
                celda.setCellStyle(cabecera);
                ancho[c] = columnas.get(c).length();
            }
            for (int f = 0; f < filas.size(); f++) {
                Row fila = hoja.createRow(f + 1);
                List<Object> valores = filas.get(f);
                for (int c = 0; c < valores.size(); c++) {
                    Object v = valores.get(c);
                    Cell celda = fila.createCell(c);
                    if (v instanceof BigDecimal n) {
                        celda.setCellValue(n.doubleValue());
                        celda.setCellStyle(decimal);
                    } else if (v instanceof Number n) {
                        celda.setCellValue(n.doubleValue());
                    } else if (v instanceof LocalDate d) {
                        celda.setCellValue(d);
                        celda.setCellStyle(fecha);
                    } else if (v instanceof LocalDateTime d) {
                        celda.setCellValue(d);
                        celda.setCellStyle(fechaHora);
                    } else if (v != null) {
                        celda.setCellValue(v.toString());
                    }
                    ancho[c] = Math.max(ancho[c], texto(v).length());
                }
            }
            // Ancho fijo segun el contenido: autoSizeColumn necesita fuentes del sistema que el contenedor no trae.
            for (int c = 0; c < ancho.length; c++) {
                hoja.setColumnWidth(c, Math.min(60, ancho[c] + 3) * 256);
            }
            hoja.createFreezePane(0, 1);
            if (!columnas.isEmpty()) {
                hoja.setAutoFilter(new CellRangeAddress(0, Math.max(0, filas.size()), 0, columnas.size() - 1));
            }
            libro.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String texto(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal n) {
            return n.toPlainString();
        }
        if (v instanceof LocalDateTime d) {
            return FECHA_HORA.format(d);
        }
        return v.toString();
    }

    private static String campo(String s) {
        boolean comillas = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String limpio = s;
        // Evita inyeccion de formulas al abrir el CSV en Excel (=, +, -, @ al inicio de un texto).
        if (!s.isEmpty() && "=+@".indexOf(s.charAt(0)) >= 0) {
            limpio = "'" + s;
        }
        return comillas ? '"' + limpio.replace("\"", "\"\"") + '"' : limpio;
    }
}
