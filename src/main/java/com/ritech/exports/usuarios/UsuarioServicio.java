package com.ritech.exports.usuarios;

import com.ritech.exports.comun.ApiException;
import com.ritech.exports.seguridad.UsuarioActual;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UsuarioServicio {

    public record UsuarioCrear(
            @NotBlank @Size(max = 120) String nombreCompleto,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String password,
            @NotBlank String rol,
            Integer idCliente) {
    }

    public record UsuarioEditar(
            @NotBlank @Size(max = 120) String nombreCompleto,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank String rol,
            Integer idCliente,
            @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String password) {
    }

    public record CambioEstado(
            @NotBlank @Pattern(regexp = "ACTIVO|INACTIVO|BLOQUEADO") String estado) {
    }

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final PasswordEncoder codificador;

    public UsuarioServicio(UsuarioRepositorio usuarios, RolRepositorio roles, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.findAllByOrderByNombreCompletoAsc();
    }

    @Transactional(readOnly = true)
    public List<Rol> roles() {
        return roles.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("El usuario"));
    }

    public Usuario crear(UsuarioCrear s) {
        String email = s.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflicto("Ya existe un usuario con ese correo.");
        }
        Rol rol = rol(s.rol());
        Usuario u = new Usuario();
        u.setNombreCompleto(s.nombreCompleto().trim());
        u.setEmail(email);
        u.setPasswordHash(codificador.encode(s.password()));
        u.setRol(rol);
        u.setIdCliente(clienteSegunRol(rol, s.idCliente(), null));
        u.setEstado("ACTIVO");
        return usuarios.saveAndFlush(u);
    }

    public Usuario editar(Integer id, UsuarioEditar s) {
        Usuario u = obtener(id);
        String email = s.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && usuarios.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflicto("Ya existe un usuario con ese correo.");
        }
        Rol rol = rol(s.rol());
        if (esUnoMismo(id) && !rol.getNombreRol().equals(u.getRol().getNombreRol())) {
            throw ApiException.invalido("No puede cambiar su propio rol.");
        }
        u.setNombreCompleto(s.nombreCompleto().trim());
        u.setEmail(email);
        u.setRol(rol);
        u.setIdCliente(clienteSegunRol(rol, s.idCliente(), u.getIdUsuario()));
        if (s.password() != null && !s.password().isBlank()) {
            u.setPasswordHash(codificador.encode(s.password()));
        }
        return usuarios.saveAndFlush(u);
    }

    /** "Inactivar" en lugar de borrar: el usuario queda referenciado por lotes, ordenes y auditoria. */
    public Usuario cambiarEstado(Integer id, String estado) {
        if (esUnoMismo(id) && !"ACTIVO".equals(estado)) {
            throw ApiException.invalido("No puede inactivar ni bloquear su propia cuenta.");
        }
        Usuario u = obtener(id);
        u.setEstado(estado);
        return usuarios.saveAndFlush(u);
    }

    private Rol rol(String nombre) {
        return roles.findByNombreRol(nombre.trim().toUpperCase())
                .orElseThrow(() -> ApiException.invalido("El rol '" + nombre + "' no existe."));
    }

    /** Una cuenta CLIENTE_UE debe apuntar a su ficha comercial; los demas roles no llevan cliente. */
    private Integer clienteSegunRol(Rol rol, Integer idCliente, Integer idUsuarioActual) {
        if (!Rol.CLIENTE_UE.equals(rol.getNombreRol())) {
            return null;
        }
        if (idCliente == null) {
            throw ApiException.invalido("Un usuario con rol CLIENTE_UE debe indicar idCliente.");
        }
        usuarios.findByIdCliente(idCliente)
                .filter(o -> !o.getIdUsuario().equals(idUsuarioActual))
                .ifPresent(o -> {
                    throw ApiException.conflicto("Ese cliente ya tiene una cuenta de acceso.");
                });
        return idCliente;
    }

    private static boolean esUnoMismo(Integer id) {
        UsuarioActual a = UsuarioActual.actualONulo();
        return a != null && a.idUsuario().equals(id);
    }
}
