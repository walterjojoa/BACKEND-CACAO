package com.ritech.exports.comercial;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClienteRepositorio extends JpaRepository<ClienteUe, Integer> {

    boolean existsByNumeroEori(String numeroEori);

    /** SCRUM-12: filtro por pais, razon social (contiene) y estado; todos opcionales. */
    @Query("""
            select c from ClienteUe c
             where (:idPais is null or c.idPais = :idPais)
               and (:estado is null or c.estado = :estado)
               and (:razonSocial is null or lower(c.razonSocial) like lower(concat('%', :razonSocial, '%')))
             order by c.razonSocial""")
    List<ClienteUe> buscar(Integer idPais, String razonSocial, String estado);
}
