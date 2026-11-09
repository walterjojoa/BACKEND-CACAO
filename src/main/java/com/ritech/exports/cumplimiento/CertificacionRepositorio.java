package com.ritech.exports.cumplimiento;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificacionRepositorio extends JpaRepository<Certificacion, Integer> {

    List<Certificacion> findAllByOrderByObligatoriaUeDescNombreAsc();
}
