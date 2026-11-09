package com.ritech.exports.logistica;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoRepositorio extends JpaRepository<SeguimientoEnvio, Integer> {

    List<SeguimientoEnvio> findByIdEnvioOrderByFechaEventoAscIdSeguimientoAsc(Integer idEnvio);
}
