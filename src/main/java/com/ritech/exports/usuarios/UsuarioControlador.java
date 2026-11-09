package com.ritech.exports.usuarios;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
@Tag(name = "Usuarios", description = "SCRUM-9: crear, editar e inactivar usuarios y asignar rol")
public class UsuarioControlador {

    private final UsuarioServicio servicio;

    public UsuarioControlador(UsuarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/usuarios")
    public List<Usuario> listar() {
        return servicio.listar();
    }

    @GetMapping("/usuarios/{id}")
    public Usuario obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario crear(@Valid @RequestBody UsuarioServicio.UsuarioCrear s) {
        return servicio.crear(s);
    }

    @PutMapping("/usuarios/{id}")
    public Usuario editar(@PathVariable Integer id, @Valid @RequestBody UsuarioServicio.UsuarioEditar s) {
        return servicio.editar(id, s);
    }

    @PatchMapping("/usuarios/{id}/estado")
    @Operation(summary = "Activa, inactiva o bloquea una cuenta")
    public Usuario cambiarEstado(@PathVariable Integer id, @Valid @RequestBody UsuarioServicio.CambioEstado s) {
        return servicio.cambiarEstado(id, s.estado());
    }

    @GetMapping("/roles")
    @Operation(summary = "Roles disponibles con sus permisos")
    public List<Rol> roles() {
        return servicio.roles();
    }
}
