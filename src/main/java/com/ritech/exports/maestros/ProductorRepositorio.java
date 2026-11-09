package com.ritech.exports.maestros;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductorRepositorio extends JpaRepository<Productor, Integer> {

    boolean existsByNitOCedula(String nitOCedula);

    List<Productor> findAllByOrderByNombreAsc();
}
