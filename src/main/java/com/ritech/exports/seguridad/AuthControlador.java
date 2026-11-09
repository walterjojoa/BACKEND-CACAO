package com.ritech.exports.seguridad;

import com.ritech.exports.usuarios.Usuario;
import com.ritech.exports.usuarios.UsuarioRepositorio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacion", description = "SCRUM-8: inicio de sesion por correo y contrasena")
public class AuthControlador {

    public record LoginSolicitud(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record LoginRespuesta(String token, String tipo, long expiraEnSegundos, Perfil usuario) {
    }

    public record Perfil(Integer idUsuario, String nombreCompleto, String email, String rol,
                        Integer idCliente, Set<String> permisos) {
    }

    private static final String CREDENCIALES_INVALIDAS = "Correo o contrasena incorrectos.";

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final JwtServicio jwt;

    public AuthControlador(UsuarioRepositorio usuarios, PasswordEncoder codificador, JwtServicio jwt) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Transactional
    @Operation(summary = "Inicia sesion y devuelve un token JWT")
    public LoginRespuesta login(@Valid @RequestBody LoginSolicitud s) {
        Usuario u = usuarios.findByEmailIgnoreCase(s.email().trim())
                .orElseThrow(() -> new BadCredentialsException(CREDENCIALES_INVALIDAS));
        // Mismo mensaje para correo inexistente y clave errada: no revela que cuentas existen.
        if (!codificador.matches(s.password(), u.getPasswordHash())) {
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        if (!"ACTIVO".equals(u.getEstado())) {
            throw new BadCredentialsException("La cuenta esta " + u.getEstado().toLowerCase() + ".");
        }
        u.setUltimoAcceso(LocalDateTime.now());
        return new LoginRespuesta(jwt.emitir(u), "Bearer", jwt.segundosValidez(), perfil(u));
    }

    @GetMapping("/yo")
    @Operation(summary = "Datos y permisos del usuario autenticado")
    public Perfil yo() {
        UsuarioActual a = UsuarioActual.actual();
        return usuarios.findById(a.idUsuario()).map(AuthControlador::perfil).orElseThrow();
    }

    static Perfil perfil(Usuario u) {
        Set<String> permisos = u.getRol().getPermisos().stream()
                .map(p -> p.getCodigo()).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        return new Perfil(u.getIdUsuario(), u.getNombreCompleto(), u.getEmail(), u.getRol().getNombreRol(),
                u.getIdCliente(), permisos);
    }
}
