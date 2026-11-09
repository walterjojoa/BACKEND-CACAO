package com.ritech.exports.auditoria;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Auditoria", description = "SCRUM-10: rastro de creaciones, ediciones y eliminaciones")
public class AuditoriaControlador {

    private final AuditoriaRepositorio repositorio;

    public AuditoriaControlador(AuditoriaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
    @Operation(summary = "Consulta paginada del rastro de auditoria")
    public Page<Auditoria> listar(@RequestParam(required = false) String tabla,
                                  @RequestParam(required = false) Integer idUsuario,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  @RequestParam(defaultValue = "50") int tamano) {
        return repositorio.buscar(tabla, idUsuario,
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 200)));
    }
}
