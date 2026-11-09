package com.ritech.exports.ordenes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ordenes")
@Tag(name = "Ordenes de exportacion", description = "SCRUM-25 y SCRUM-26")
public class OrdenControlador {

    private final OrdenServicio servicio;

    public OrdenControlador(OrdenServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','ROLE_CLIENTE_UE')")
    @Operation(summary = "Lista ordenes; un CLIENTE_UE solo ve las suyas")
    public List<OrdenExportacion> buscar(@RequestParam(required = false) Integer idCliente,
                                         @RequestParam(required = false) String estado) {
        return servicio.buscar(idCliente, estado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','ROLE_CLIENTE_UE')")
    public OrdenServicio.Detalle detalle(@PathVariable Integer id) {
        return servicio.detalle(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea la orden en BORRADOR con numero ORD-AAAA-NNNN")
    public OrdenExportacion crear(@Valid @RequestBody OrdenServicio.OrdenSolicitud s) {
        return servicio.crear(s);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    public OrdenExportacion editar(@PathVariable Integer id, @Valid @RequestBody OrdenServicio.OrdenSolicitud s) {
        return servicio.editar(id, s);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @Operation(summary = "BORRADOR -> CONFIRMADA -> EN_TRANSITO -> ENTREGADA, o ANULADA (libera los lotes)")
    public OrdenExportacion cambiarEstado(@PathVariable Integer id, @Valid @RequestBody OrdenServicio.CambioEstado s) {
        return servicio.cambiarEstado(id, s.estado());
    }

    @PostMapping("/{id}/lotes")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Asigna un lote APROBADO; revalida la normativa y controla los kg disponibles")
    public OrdenServicio.Detalle asignar(@PathVariable Integer id,
                                         @Valid @RequestBody OrdenServicio.AsignacionSolicitud s) {
        return servicio.asignar(id, s);
    }

    @PutMapping("/{id}/lotes/{idLote}")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    public OrdenServicio.Detalle editarAsignacion(@PathVariable Integer id, @PathVariable Integer idLote,
                                                  @Valid @RequestBody OrdenServicio.AsignacionEditar s) {
        return servicio.editarAsignacion(id, idLote, s);
    }

    @DeleteMapping("/{id}/lotes/{idLote}")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @Operation(summary = "Retira el lote de la orden; vuelve a APROBADO")
    public OrdenServicio.Detalle quitar(@PathVariable Integer id, @PathVariable Integer idLote) {
        return servicio.quitar(id, idLote);
    }
}
