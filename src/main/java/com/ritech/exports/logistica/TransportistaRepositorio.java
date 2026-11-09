package com.ritech.exports.logistica;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportistaRepositorio extends JpaRepository<Transportista, Integer> {

    List<Transportista> findAllByOrderByNombreAsc();
}
