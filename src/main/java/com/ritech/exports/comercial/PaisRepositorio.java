package com.ritech.exports.comercial;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaisRepositorio extends JpaRepository<Pais, Integer> {

    List<Pais> findAllByOrderByNombreAsc();

    List<Pais> findByPerteneceUeOrderByNombreAsc(Boolean perteneceUe);
}
