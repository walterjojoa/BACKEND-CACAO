package com.ritech.exports.lotes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteFincaRepositorio extends JpaRepository<LoteFinca, LoteFinca.Clave> {

    List<LoteFinca> findByIdLoteOrderByPorcentajeAporteDesc(Integer idLote);

    boolean existsByIdLote(Integer idLote);
}
