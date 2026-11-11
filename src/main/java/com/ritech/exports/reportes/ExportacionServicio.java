package com.ritech.exports.reportes;

import com.ritech.exports.comun.ApiException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-32: ordenes, lotes y envios para descargar, filtrados por fecha. Los filtros son inclusivos;
 * los dos son opcionales.
 */
@Service
@Transactional(readOnly = true)
public class ExportacionServicio {

    public enum Reporte { ORDENES, LOTES, ENVIOS }

    private final JdbcClient jdbc;

    public ExportacionServicio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Tabla generar(Reporte reporte, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ApiException.invalido("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        return switch (reporte) {
            case ORDENES -> consultar("ordenes", """
                    select o.numero_orden as "Orden", o.fecha_orden as "Fecha", c.razon_social as "Cliente",
                           c.numero_eori as "EORI", pa.codigo_iso2 as "Pais", o.incoterm as "Incoterm",
                           o.moneda as "Moneda", o.valor_total as "Valor total", o.estado_orden as "Estado",
                           (select count(*) from ordenes_lotes ol where ol.id_orden = o.id_orden) as "Lotes",
                           (select coalesce(sum(ol.kg_asignados), 0) from ordenes_lotes ol
                             where ol.id_orden = o.id_orden) as "Kg"
                      from ordenes_exportacion o
                      join clientes_ue c on c.id_cliente = o.id_cliente
                      join paises pa on pa.id_pais = c.id_pais
                     where 1 = 1""", "o.fecha_orden", "o.fecha_orden, o.id_orden", desde, hasta);
            case LOTES -> consultar("lotes", """
                    select l.codigo_lote as "Lote", p.tipo as "Tipo", p.nombre_comercial as "Producto",
                           p.codigo_hs as "Codigo HS", l.peso_neto_kg as "Peso neto kg",
                           l.peso_bruto_kg as "Peso bruto kg", l.fecha_cosecha as "Fecha cosecha",
                           l.fecha_registro as "Fecha registro", l.estado_lote as "Estado",
                           (select d.dictamen from v_dictamen_lote d where d.id_lote = l.id_lote) as "Dictamen",
                           (select count(*) from lotes_fincas lf where lf.id_lote = l.id_lote) as "Fincas",
                           (select max(o.numero_orden) from ordenes_lotes ol
                              join ordenes_exportacion o on o.id_orden = ol.id_orden
                             where ol.id_lote = l.id_lote and o.estado_orden <> 'ANULADA') as "Orden"
                      from lotes_exportacion l
                      join productos p on p.id_producto = l.id_producto
                     where 1 = 1""", "l.fecha_registro", "l.fecha_registro, l.id_lote", desde, hasta);
            case ENVIOS -> consultar("envios", """
                    select e.id_envio as "Envio", o.numero_orden as "Orden", c.razon_social as "Cliente",
                           t.nombre as "Transportista", t.tipo as "Tipo", e.numero_contenedor as "Contenedor",
                           e.booking_bl as "Booking BL", e.puerto_origen as "Puerto origen",
                           e.puerto_destino as "Puerto destino", e.fecha_estimada_zarpe as "Zarpe estimado",
                           e.fecha_estimada_arribo as "Arribo estimado", e.estado_envio as "Estado"
                      from envios e
                      join ordenes_exportacion o on o.id_orden = e.id_orden
                      join clientes_ue c on c.id_cliente = o.id_cliente
                      join transportistas t on t.id_transportista = e.id_transportista
                     where 1 = 1""",
                    // Un envio sin zarpe programado se ubica por la fecha en que se registro.
                    "coalesce(e.fecha_estimada_zarpe, e.created_at)",
                    "coalesce(e.fecha_estimada_zarpe, e.created_at), e.id_envio", desde, hasta);
        };
    }

    /** El campo de fecha se compara contra [desde 00:00, hasta+1 00:00) para que sirva con DATE y DATETIME. */
    private Tabla consultar(String nombre, String base, String campoFecha, String orden, LocalDate desde,
                            LocalDate hasta) {
        StringBuilder sql = new StringBuilder(base);
        List<Object> params = new ArrayList<>();
        if (desde != null) {
            sql.append(" and ").append(campoFecha).append(" >= ?");
            params.add(Date.valueOf(desde));
        }
        if (hasta != null) {
            sql.append(" and ").append(campoFecha).append(" < ?");
            params.add(Date.valueOf(hasta.plusDays(1)));
        }
        sql.append(" order by ").append(orden);
        return jdbc.sql(sql.toString()).params(params).query((ResultSet rs) -> {
            // Los encabezados salen de los alias aunque no haya filas.
            ResultSetMetaData md = rs.getMetaData();
            List<String> columnas = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columnas.add(md.getColumnLabel(i));
            }
            List<List<Object>> filas = new ArrayList<>();
            while (rs.next()) {
                List<Object> fila = new ArrayList<>(columnas.size());
                for (int i = 1; i <= columnas.size(); i++) {
                    fila.add(valor(rs.getObject(i)));
                }
                filas.add(fila);
            }
            return new Tabla(nombre, columnas, filas);
        });
    }

    private static Object valor(Object v) {
        if (v instanceof Date d) {
            return d.toLocalDate();
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (v instanceof Boolean || v instanceof Number || v instanceof String || v == null
                || v instanceof java.time.temporal.Temporal) {
            return v;
        }
        return v.toString();
    }
}
