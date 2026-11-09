package com.ritech.exports.ordenes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenLoteRepositorio extends JpaRepository<OrdenLote, OrdenLote.Clave> {

    List<OrdenLote> findByIdOrden(Integer idOrden);
}
