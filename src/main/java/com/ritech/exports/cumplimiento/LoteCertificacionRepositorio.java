package com.ritech.exports.cumplimiento;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoteCertificacionRepositorio extends JpaRepository<LoteCertificacion, LoteCertificacion.Clave> {

    List<LoteCertificacion> findByIdLoteOrderByFechaVencimientoAsc(Integer idLote);

    /** SCRUM-23: vigentes que vencen entre hoy y el limite. */
    @Query("""
            select c from LoteCertificacion c
             where c.estadoValidacion = 'VIGENTE'
               and c.fechaVencimiento >= :hoy and c.fechaVencimiento <= :limite
             order by c.fechaVencimiento""")
    List<LoteCertificacion> porVencer(LocalDate hoy, LocalDate limite);

    /** Marca VENCIDO lo que ya paso su fecha; lo ejecuta la tarea diaria. */
    @Modifying
    @Query("""
            update LoteCertificacion c set c.estadoValidacion = 'VENCIDO'
             where c.estadoValidacion = 'VIGENTE' and c.fechaVencimiento <= :hoy""")
    int marcarVencidos(LocalDate hoy);
}
