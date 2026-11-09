package com.ritech.exports.seguridad;

import com.ritech.exports.comun.ApiException;
import com.ritech.exports.usuarios.Rol;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Quien hace la peticion: lo deja el JwtFiltro como principal de Spring Security. */
public record UsuarioActual(
        Integer idUsuario,
        String email,
        String rol,
        Integer idCliente,
        Set<String> permisos) {

    public boolean esCliente() {
        return Rol.CLIENTE_UE.equals(rol);
    }

    /** El usuario autenticado, o null si la peticion es anonima (login, docs). */
    public static UsuarioActual actualONulo() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioActual u) {
            return u;
        }
        return null;
    }

    public static UsuarioActual actual() {
        UsuarioActual u = actualONulo();
        if (u == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesion.");
        }
        return u;
    }
}
