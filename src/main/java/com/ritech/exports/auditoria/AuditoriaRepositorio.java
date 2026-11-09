package com.ritech.exports.auditoria;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditoriaRepositorio extends JpaRepository<Auditoria, Long> {

    @Query("""
            select a from Auditoria a
             where (:tabla is null or a.tablaAfectada = :tabla)
               and (:idUsuario is null or a.idUsuario = :idUsuario)
               and (:desde is null or a.fechaHora >= :desde)
               and (:hasta is null or a.fechaHora < :hasta)
             order by a.fechaHora desc, a.idAuditoria desc""")
    Page<Auditoria> buscar(String tabla, Integer idUsuario, LocalDateTime desde, LocalDateTime hasta,
                           Pageable pagina);
}
