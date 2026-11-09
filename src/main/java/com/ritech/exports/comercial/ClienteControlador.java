package com.ritech.exports.comercial;

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
@Tag(name = "Clientes UE", description = "SCRUM-11 y SCRUM-12: importadores europeos")
public class ClienteControlador {

    private final ClienteServicio servicio;
    private final PaisRepositorio paises;

    public ClienteControlador(ClienteServicio servicio, PaisRepositorio paises) {
        this.servicio = servicio;
        this.paises = paises;
    }

    @GetMapping("/paises")
    @Operation(summary = "Catalogo de paises; ue=true deja solo los miembros de la UE")
    public List<Pais> paises(@RequestParam(required = false) Boolean ue) {
        return ue == null ? paises.findAllByOrderByNombreAsc() : paises.findByPerteneceUeOrderByNombreAsc(ue);
    }

    @GetMapping("/clientes")
    @PreAuthorize("hasAuthority('CLIENTES_CONSULTAR')")
    @Operation(summary = "Consulta y filtra clientes por pais, razon social y estado")
    public List<ClienteUe> buscar(@RequestParam(required = false) Integer idPais,
                                  @RequestParam(required = false) String razonSocial,
                                  @RequestParam(required = false) String estado) {
        return servicio.buscar(idPais, razonSocial, estado);
    }

    @GetMapping("/clientes/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_CONSULTAR')")
    public ClienteUe obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @PostMapping("/clientes")
    @PreAuthorize("hasAuthority('CLIENTES_GESTIONAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteUe crear(@Valid @RequestBody ClienteServicio.ClienteSolicitud s) {
        return servicio.crear(s);
    }

    @PutMapping("/clientes/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_GESTIONAR')")
    public ClienteUe editar(@PathVariable Integer id, @Valid @RequestBody ClienteServicio.ClienteSolicitud s) {
        return servicio.editar(id, s);
    }
}
