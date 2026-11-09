package com.ritech.exports.cumplimiento;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglaRepositorio extends JpaRepository<ReglaNormativa, Integer> {

    boolean existsByCodigoRegla(String codigoRegla);

    List<ReglaNormativa> findAllByOrderByCodigoReglaAsc();
}
