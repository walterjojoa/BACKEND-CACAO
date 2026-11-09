package com.ritech.exports.maestros;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepositorio extends JpaRepository<Producto, Integer> {

    List<Producto> findAllByOrderByTipoAscNombreComercialAsc();
}
