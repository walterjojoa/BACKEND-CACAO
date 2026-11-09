package com.ritech.exports.logistica;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnvioRepositorio extends JpaRepository<Envio, Integer> {

    boolean existsByBookingBl(String bookingBl);

    List<Envio> findByIdOrden(Integer idOrden);

    @Query(value = """
            select e.* from envios e
              join ordenes_exportacion o on o.id_orden = e.id_orden
             where (:estado is null or e.estado_envio = :estado)
               and (:idCliente is null or o.id_cliente = :idCliente)
             order by e.fecha_estimada_arribo is null, e.fecha_estimada_arribo, e.id_envio desc""",
            nativeQuery = true)
    List<Envio> buscar(String estado, Integer idCliente);
}
