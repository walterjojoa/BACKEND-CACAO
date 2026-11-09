package com.ritech.exports.ordenes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrdenRepositorio extends JpaRepository<OrdenExportacion, Integer> {

    boolean existsByNumeroOrden(String numeroOrden);

    @Query("""
            select o from OrdenExportacion o
             where (:idCliente is null or o.idCliente = :idCliente)
               and (:estado is null or o.estadoOrden = :estado)
             order by o.fechaOrden desc, o.idOrden desc""")
    List<OrdenExportacion> buscar(Integer idCliente, String estado);

    @Query(value = """
            select coalesce(max(cast(substring(numero_orden, char_length(:prefijo) + 1) as unsigned)), 0)
              from ordenes_exportacion where numero_orden like concat(:prefijo, '%')""", nativeQuery = true)
    long ultimoConsecutivo(String prefijo);

    /** La base no tiene trigger para EXPORTADO: al zarpar la orden sus lotes salen del inventario. */
    @Modifying
    @Query(value = """
            update lotes_exportacion set estado_lote = 'EXPORTADO'
             where estado_lote = 'ASIGNADO'
               and id_lote in (select id_lote from ordenes_lotes where id_orden = :idOrden)""", nativeQuery = true)
    int marcarLotesExportados(Integer idOrden);
}
