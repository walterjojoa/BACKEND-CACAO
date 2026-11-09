package com.ritech.exports.usuarios;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepositorio extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByNombreRol(String nombreRol);
}
