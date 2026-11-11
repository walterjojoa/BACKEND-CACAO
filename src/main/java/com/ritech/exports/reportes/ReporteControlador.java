package com.ritech.exports.reportes;

import com.ritech.exports.comun.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes", description = "SCRUM-31 tablero de indicadores; SCRUM-32 exportar a Excel o CSV")
public class ReporteControlador {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TableroServicio tablero;
    private final ExportacionServicio exportacion;

    public ReporteControlador(TableroServicio tablero, ExportacionServicio exportacion) {
        this.tablero = tablero;
        this.exportacion = exportacion;
    }

    @GetMapping("/tablero")
    @PreAuthorize("isAuthenticated() and !hasAuthority('ROLE_CLIENTE_UE')")
    @Operation(summary = "Indicadores para la pantalla de inicio",
            description = "Ordenes por estado, kg exportados, valor vendido por mes y moneda (12 meses), lotes "
                    + "aprobados y rechazados, y envios en transito o con incidencia. Lo ve todo el personal "
                    + "interno, no los clientes UE.")
    public TableroServicio.Tablero tablero() {
        return tablero.tablero();
    }

    @GetMapping("/{reporte}")
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','AUDITORIA_CONSULTAR')")
    @Operation(summary = "Descarga ordenes, lotes o envios en Excel (xlsx) o CSV",
            description = "reporte = ordenes | lotes | envios. Fechas en formato AAAA-MM-DD, inclusivas y "
                    + "opcionales: ordenes por fecha_orden, lotes por fecha de registro, envios por zarpe estimado.")
    public ResponseEntity<byte[]> exportar(
            @PathVariable String reporte,
            @Parameter(description = "xlsx (por defecto) o csv") @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ExportacionServicio.Reporte r;
        try {
            r = ExportacionServicio.Reporte.valueOf(reporte.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.noEncontrado("El reporte '" + reporte + "' (use ordenes, lotes o envios)");
        }
        String f = formato.trim().toLowerCase(Locale.ROOT);
        if (!f.equals("xlsx") && !f.equals("csv")) {
            throw ApiException.invalido("Formato no soportado: use xlsx o csv.");
        }
        Tabla t = exportacion.generar(r, desde, hasta);
        String nombre = t.nombre() + (desde == null ? "" : "_desde_" + desde) + (hasta == null ? "" : "_hasta_" + hasta)
                + "." + f;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .contentType(f.equals("csv") ? new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)
                        : MediaType.parseMediaType(XLSX))
                .body(f.equals("csv") ? t.csv() : t.xlsx());
    }
}
