package com.ritech.exports.lotes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspeccionRepositorio extends JpaRepository<InspeccionCalidad, Integer> {

    List<InspeccionCalidad> findByIdLoteOrderByFechaInspeccionDescIdInspeccionDesc(Integer idLote);
}
