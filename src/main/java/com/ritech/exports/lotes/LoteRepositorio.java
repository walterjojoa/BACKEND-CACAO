package com.ritech.exports.lotes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoteRepositorio extends JpaRepository<LoteExportacion, Integer> {

    Optional<LoteExportacion> findByCodigoLote(String codigoLote);

    boolean existsByCodigoLote(String codigoLote);

    @Query("""
            select l from LoteExportacion l
             where (:estado is null or l.estadoLote = :estado)
               and (:idProducto is null or l.idProducto = :idProducto)
             order by l.fechaRegistro desc, l.idLote desc""")
    List<LoteExportacion> buscar(String estado, Integer idProducto);

    /** Lotes que el cliente compro: los que estan en alguna de sus ordenes. */
    @Query(value = """
            select distinct l.* from lotes_exportacion l
              join ordenes_lotes ol       on ol.id_lote  = l.id_lote
              join ordenes_exportacion o  on o.id_orden  = ol.id_orden
             where o.id_cliente = :idCliente
             order by l.id_lote desc""", nativeQuery = true)
    List<LoteExportacion> delCliente(Integer idCliente);

    @Query(value = """
            select count(*) from ordenes_lotes ol
              join ordenes_exportacion o on o.id_orden = ol.id_orden
             where ol.id_lote = :idLote and o.id_cliente = :idCliente""", nativeQuery = true)
    long compradoPor(Integer idLote, Integer idCliente);

    /** Ultimo consecutivo usado para un prefijo, p. ej. CAF-2026-. */
    @Query(value = """
            select coalesce(max(cast(substring(codigo_lote, char_length(:prefijo) + 1) as unsigned)), 0)
              from lotes_exportacion
             where codigo_lote like concat(:prefijo, '%')""", nativeQuery = true)
    long ultimoConsecutivo(String prefijo);
}
