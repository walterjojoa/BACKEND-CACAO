package com.ritech.exports.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ritech.exports.Sesion;
import com.ritech.exports.comun.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** SCRUM-9: crear, editar e inactivar usuarios y asignar rol. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServicioTest {

    @Mock
    UsuarioRepositorio usuarios;
    @Mock
    RolRepositorio roles;

    private final PasswordEncoder codificador = new BCryptPasswordEncoder(4);
    private UsuarioServicio servicio;

    private static Rol rol(int id, String nombre) {
        Rol r = new Rol();
        r.setIdRol(id);
        r.setNombreRol(nombre);
        return r;
    }

    @BeforeEach
    void preparar() {
        servicio = new UsuarioServicio(usuarios, roles, codificador);
        when(roles.findByNombreRol(Rol.ADMINISTRADOR)).thenReturn(Optional.of(rol(1, Rol.ADMINISTRADOR)));
        when(roles.findByNombreRol(Rol.GESTOR)).thenReturn(Optional.of(rol(2, Rol.GESTOR)));
        when(roles.findByNombreRol(Rol.CLIENTE_UE)).thenReturn(Optional.of(rol(4, Rol.CLIENTE_UE)));
        when(usuarios.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Sesion.como(Rol.ADMINISTRADOR, 1, null, "USUARIOS_GESTIONAR");
    }

    @AfterEach
    void limpiar() {
        Sesion.limpiar();
    }

    @Test
    void guardaLaContrasenaConBcryptYElCorreoEnMinusculas() {
        Usuario u = servicio.crear(new UsuarioServicio.UsuarioCrear("Ana Ruiz", "Ana@RiTech.co", "ClaveSegura1",
                "gestor_exportaciones", null));

        assertThat(u.getEmail()).isEqualTo("ana@ritech.co");
        assertThat(u.getPasswordHash()).startsWith("$2").isNotEqualTo("ClaveSegura1");
        assertThat(codificador.matches("ClaveSegura1", u.getPasswordHash())).isTrue();
        assertThat(u.getEstado()).isEqualTo("ACTIVO");
        assertThat(u.getIdCliente()).as("un gestor no lleva cliente").isNull();
    }

    @Test
    void rechazaUnCorreoRepetido() {
        when(usuarios.existsByEmailIgnoreCase("ana@ritech.co")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new UsuarioServicio.UsuarioCrear("Ana", "ana@ritech.co",
                "ClaveSegura1", Rol.GESTOR, null))).hasMessageContaining("correo");
    }

    @Test
    void unClienteUeDebeApuntarASuFichaComercial() {
        assertThatThrownBy(() -> servicio.crear(new UsuarioServicio.UsuarioCrear("Importadora", "c@eu.example",
                "ClaveSegura1", Rol.CLIENTE_UE, null))).hasMessageContaining("idCliente");
    }

    @Test
    void unaFichaComercialSoloTieneUnaCuenta() {
        Usuario otro = new Usuario();
        otro.setIdUsuario(9);
        when(usuarios.findByIdCliente(1)).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> servicio.crear(new UsuarioServicio.UsuarioCrear("Importadora", "c@eu.example",
                "ClaveSegura1", Rol.CLIENTE_UE, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ya tiene una cuenta");
    }

    @Test
    void elRolInexistenteSeRechaza() {
        assertThatThrownBy(() -> servicio.crear(new UsuarioServicio.UsuarioCrear("X", "x@ritech.co",
                "ClaveSegura1", "SUPERUSUARIO", null))).hasMessageContaining("no existe");
    }

    @Test
    void nadiePuedeInactivarSuPropiaCuenta() {
        assertThatThrownBy(() -> servicio.cambiarEstado(1, "INACTIVO")).hasMessageContaining("propia cuenta");
    }

    @Test
    void nadiePuedeCambiarseSuPropioRol() {
        Usuario yo = new Usuario();
        yo.setIdUsuario(1);
        yo.setEmail("admin@ritech.co");
        yo.setRol(rol(1, Rol.ADMINISTRADOR));
        when(usuarios.findById(1)).thenReturn(Optional.of(yo));

        assertThatThrownBy(() -> servicio.editar(1, new UsuarioServicio.UsuarioEditar("Admin", "admin@ritech.co",
                Rol.GESTOR, null, null))).hasMessageContaining("propio rol");
    }

    @Test
    void inactivarAOtroUsuarioFunciona() {
        Usuario otro = new Usuario();
        otro.setIdUsuario(3);
        otro.setEstado("ACTIVO");
        when(usuarios.findById(3)).thenReturn(Optional.of(otro));

        assertThat(servicio.cambiarEstado(3, "INACTIVO").getEstado()).isEqualTo("INACTIVO");
    }
}
