package com.ritech.exports.seguridad;

import com.ritech.exports.usuarios.Permiso;
import com.ritech.exports.usuarios.UsuarioRepositorio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee "Authorization: Bearer <token>". Los permisos se cargan de la base en cada peticion,
 * asi que inactivar un usuario o cambiarle el rol surte efecto de inmediato.
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private final JwtServicio jwt;
    private final UsuarioRepositorio usuarios;

    public JwtFiltro(JwtServicio jwt, UsuarioRepositorio usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = req.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            jwt.validar(cabecera.substring(7).trim())
                    .flatMap(usuarios::findById)
                    .filter(u -> "ACTIVO".equals(u.getEstado()))
                    .ifPresent(u -> {
                        Set<String> permisos = u.getRol().getPermisos().stream()
                                .map(Permiso::getCodigo).collect(Collectors.toSet());
                        UsuarioActual actual = new UsuarioActual(u.getIdUsuario(), u.getEmail(),
                                u.getRol().getNombreRol(), u.getIdCliente(), permisos);
                        List<SimpleGrantedAuthority> autoridades = new java.util.ArrayList<>(
                                permisos.stream().map(SimpleGrantedAuthority::new).toList());
                        autoridades.add(new SimpleGrantedAuthority("ROLE_" + actual.rol()));
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(actual, null, autoridades));
                    });
        }
        chain.doFilter(req, res);
    }
}
