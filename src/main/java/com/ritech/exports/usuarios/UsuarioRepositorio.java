package com.ritech.exports.usuarios;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByIdCliente(Integer idCliente);

    /** Usuarios del script semilla cuyo hash todavia es el marcador "REEMPLAZAR_POR_HASH_REAL". */
    @Query("select u from Usuario u where u.passwordHash like '%REEMPLAZAR_POR_HASH_REAL%'")
    List<Usuario> conHashMarcador();

    List<Usuario> findAllByOrderByNombreCompletoAsc();
}
