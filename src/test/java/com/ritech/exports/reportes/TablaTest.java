package com.ritech.exports.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** SCRUM-32: escritura de los reportes en CSV y Excel. */
class TablaTest {

    private final Tabla tabla = new Tabla("ordenes", List.of("Orden", "Fecha", "Cliente", "Valor total", "Registro"),
            List.of(
                    Arrays.asList("ORD-2026-0001", LocalDate.of(2026, 10, 2), "Café, Cacao & Co \"UE\"",
                            new BigDecimal("12500.50"), LocalDateTime.of(2026, 10, 2, 9, 30)),
                    Arrays.asList("ORD-2026-0002", null, "=HYPERLINK(\"x\")", new BigDecimal("-3.00"), null)));

    @Test
    void elCsvLlevaBomEscapaComasYComillasYNeutralizaFormulas() {
        String csv = new String(tabla.csv(), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Orden,Fecha,Cliente,Valor total,Registro\r\n");
        assertThat(csv).contains("ORD-2026-0001,2026-10-02,\"Café, Cacao & Co \"\"UE\"\"\",12500.50,2026-10-02 09:30:00\r\n");
        assertThat(csv).contains("ORD-2026-0002,,\"'=HYPERLINK(\"\"x\"\")\",-3.00,\r\n");
    }

    @Test
    void elExcelTieneEncabezadosNumerosYFechasReales() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(tabla.xlsx()))) {
            Sheet hoja = libro.getSheet("ordenes");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Orden");
            assertThat(hoja.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(12500.50);
            assertThat(DateUtil.isCellDateFormatted(hoja.getRow(1).getCell(1))).isTrue();
            assertThat(hoja.getRow(1).getCell(1).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 10, 2));
            assertThat(hoja.getRow(2).getCell(2).getStringCellValue()).isEqualTo("=HYPERLINK(\"x\")");
            assertThat(hoja.getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void unReporteSinFilasConservaLosEncabezados() throws IOException {
        Tabla vacia = new Tabla("lotes", List.of("Lote", "Estado"), List.of());

        assertThat(new String(vacia.csv(), StandardCharsets.UTF_8)).isEqualTo("﻿Lote,Estado\r\n");
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(vacia.xlsx()))) {
            assertThat(libro.getSheet("lotes").getRow(0).getCell(1).getStringCellValue()).isEqualTo("Estado");
        }
    }
}
