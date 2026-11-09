package com.ritech.exports.logistica;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Logistica", description = "SCRUM-28/29/30: envios y seguimiento")
public class LogisticaControlador {

    private final LogisticaServicio servicio;

    public LogisticaControlador(LogisticaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/transportistas")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    public List<Transportista> transportistas() {
        return servicio.transportistas();
    }

    @PostMapping("/transportistas")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public Transportista crearTransportista(@Valid @RequestBody LogisticaServicio.TransportistaSolicitud s) {
        return servicio.guardarTransportista(null, s);
    }

    @PutMapping("/transportistas/{id}")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    public Transportista editarTransportista(@PathVariable Integer id,
                                             @Valid @RequestBody LogisticaServicio.TransportistaSolicitud s) {
        return servicio.guardarTransportista(id, s);
    }

    @GetMapping("/envios")
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','ROLE_CLIENTE_UE')")
    @Operation(summary = "Lista envios; un CLIENTE_UE solo ve los de sus ordenes")
    public List<Envio> envios(@RequestParam(required = false) String estado) {
        return servicio.buscar(estado);
    }

    @GetMapping("/envios/{id}")
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','ROLE_CLIENTE_UE')")
    @Operation(summary = "SCRUM-30: estado, ubicacion y bitacora del envio")
    public LogisticaServicio.EnvioDetalle detalle(@PathVariable Integer id) {
        return servicio.detalle(id);
    }

    @PostMapping("/ordenes/{idOrden}/envios")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "SCRUM-28: programa el envio de una orden CONFIRMADA")
    public LogisticaServicio.EnvioDetalle programar(@PathVariable Integer idOrden,
                                                    @Valid @RequestBody LogisticaServicio.EnvioSolicitud s) {
        return servicio.programar(idOrden, s);
    }

    @PostMapping("/envios/{id}/seguimiento")
    @PreAuthorize("hasAuthority('ORDENES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "SCRUM-29: registra un evento; EN_TRANSITO y ENTREGADO avanzan la orden")
    public LogisticaServicio.EnvioDetalle evento(@PathVariable Integer id,
                                                 @Valid @RequestBody LogisticaServicio.EventoSolicitud s) {
        return servicio.registrarEvento(id, s);
    }
}
