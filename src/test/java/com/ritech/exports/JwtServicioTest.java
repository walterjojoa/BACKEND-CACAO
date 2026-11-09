package com.ritech.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ritech.exports.seguridad.JwtServicio;
import com.ritech.exports.usuarios.Rol;
import com.ritech.exports.usuarios.Usuario;
import org.junit.jupiter.api.Test;

class JwtServicioTest {

    private final JwtServicio jwt = new JwtServicio("clave-de-pruebas-ritech-0123456789-abcdef", 8);

    private static Usuario usuario(int id, String rolNombre) {
        Rol rol = new Rol();
        rol.setNombreRol(rolNombre);
        Usuario u = new Usuario();
        u.setIdUsuario(id);
        u.setEmail("u" + id + "@ritech.co");
        u.setRol(rol);
        return u;
    }

    @Test
    void elTokenEmitidoDevuelveElIdDelUsuario() {
        assertThat(jwt.validar(jwt.emitir(usuario(42, Rol.GESTOR)))).contains(42);
    }

    @Test
    void rechazaTokensFirmadosConOtraClaveOMalFormados() {
        String ajeno = new JwtServicio("otra-clave-distinta-de-32-caracteres-xx", 8).emitir(usuario(1, Rol.ADMINISTRADOR));
        assertThat(jwt.validar(ajeno)).isEmpty();
        assertThat(jwt.validar("no-es-un-token")).isEmpty();
    }

    @Test
    void exigeUnSecretoDeAlMenos32Caracteres() {
        assertThatThrownBy(() -> new JwtServicio("corta", 8)).isInstanceOf(IllegalStateException.class);
    }
}
