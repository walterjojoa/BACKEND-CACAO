package com.ritech.exports.maestros;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FincaRepositorio extends JpaRepository<Finca, Integer> {

    List<Finca> findByIdProductorOrderByNombreFincaAsc(Integer idProductor);
}
