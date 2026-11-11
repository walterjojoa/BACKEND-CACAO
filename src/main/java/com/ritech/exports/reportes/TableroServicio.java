package com.ritech.exports.reportes;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-31: indicadores de la pantalla de inicio, calculados en la base con una consulta por bloque. */
@Service
@Transactional(readOnly = true)
public class TableroServicio {

    static final List<String> ESTADOS_ORDEN = List.of("BORRADOR", "CONFIRMADA", "EN_TRANSITO", "ENTREGADA", "ANULADA");
    static final List<String> ESTADOS_LOTE = List.of("REGISTRADO", "EN_INSPECCION", "APROBADO", "RECHAZADO",
            "ASIGNADO", "EXPORTADO");
    static final List<String> ESTADOS_ENVIO = List.of("PROGRAMADO", "EN_PUERTO", "EN_TRANSITO", "ARRIBADO",
            "ENTREGADO", "INCIDENCIA");

    /** Valor vendido de un mes en una moneda (las ordenes pueden ser en EUR o USD; no se suman entre si). */
    public record ValorMes(int anio, int mes, String moneda, BigDecimal valor, long ordenes) {
    }

    public record EnvioActivo(Integer idEnvio, String numeroOrden, String cliente, String transportista,
                              String numeroContenedor, String puertoOrigen, String puertoDestino,
                              LocalDate fechaEstimadaArribo, String estadoEnvio) {
    }

    /**
     * @param lotesAprobados lotes que pasaron la inspeccion: APROBADO, ASIGNADO o EXPORTADO
     * @param kgExportados   kg asignados a ordenes EN_TRANSITO o ENTREGADA
     * @param valorPorMes    ultimos 12 meses, ordenes CONFIRMADA / EN_TRANSITO / ENTREGADA
     */
    public record Tablero(Map<String, Long> ordenesPorEstado, long ordenesTotal, BigDecimal kgExportados,
                          List<ValorMes> valorPorMes, Map<String, Long> lotesPorEstado, long lotesAprobados,
                          long lotesRechazados, Map<String, Long> enviosPorEstado, long enviosEnTransito,
                          long enviosConIncidencia, List<EnvioActivo> enviosActivos, LocalDateTime generado) {
    }

    private final JdbcClient jdbc;

    public TableroServicio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Tablero tablero() {
        Map<String, Long> ordenes = conteo("select estado_orden, count(*) from ordenes_exportacion group by estado_orden",
                ESTADOS_ORDEN);
        Map<String, Long> lotes = conteo("select estado_lote, count(*) from lotes_exportacion group by estado_lote",
                ESTADOS_LOTE);
        Map<String, Long> envios = conteo("select estado_envio, count(*) from envios group by estado_envio",
                ESTADOS_ENVIO);

        BigDecimal kg = jdbc.sql("""
                        select coalesce(sum(ol.kg_asignados), 0)
                          from ordenes_lotes ol join ordenes_exportacion o on o.id_orden = ol.id_orden
                         where o.estado_orden in ('EN_TRANSITO', 'ENTREGADA')""")
                .query(BigDecimal.class).single();

        LocalDate desde = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        List<ValorMes> valor = jdbc.sql("""
                        select year(fecha_orden), month(fecha_orden), moneda, sum(valor_total), count(*)
                          from ordenes_exportacion
                         where estado_orden in ('CONFIRMADA', 'EN_TRANSITO', 'ENTREGADA')
                           and fecha_orden >= ?
                         group by year(fecha_orden), month(fecha_orden), moneda
                         order by 1, 2, 3""")
                .param(Date.valueOf(desde))
                .query((rs, n) -> new ValorMes(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getLong(5)))
                .list();

        List<EnvioActivo> activos = jdbc.sql("""
                        select e.id_envio, o.numero_orden, c.razon_social, t.nombre, e.numero_contenedor,
                               e.puerto_origen, e.puerto_destino, e.fecha_estimada_arribo, e.estado_envio
                          from envios e
                          join ordenes_exportacion o on o.id_orden = e.id_orden
                          join clientes_ue c on c.id_cliente = o.id_cliente
                          join transportistas t on t.id_transportista = e.id_transportista
                         where e.estado_envio in ('EN_TRANSITO', 'INCIDENCIA')
                         order by case when e.estado_envio = 'INCIDENCIA' then 0 else 1 end,
                                  e.fecha_estimada_arribo, e.id_envio
                         limit 20""")
                .query((rs, n) -> {
                    Date eta = rs.getDate(8);
                    return new EnvioActivo(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7), eta == null ? null : eta.toLocalDate(),
                            rs.getString(9));
                })
                .list();

        long aprobados = lotes.get("APROBADO") + lotes.get("ASIGNADO") + lotes.get("EXPORTADO");
        return new Tablero(ordenes, ordenes.values().stream().mapToLong(Long::longValue).sum(), kg, valor, lotes,
                aprobados, lotes.get("RECHAZADO"), envios, envios.get("EN_TRANSITO"), envios.get("INCIDENCIA"),
                activos, LocalDateTime.now());
    }

    /** Conteo por estado con todos los estados presentes (en 0 si no hay filas), en el orden del flujo. */
    private Map<String, Long> conteo(String sql, List<String> estados) {
        Map<String, Long> m = new LinkedHashMap<>();
        estados.forEach(e -> m.put(e, 0L));
        jdbc.sql(sql).query(rs -> {
            m.merge(rs.getString(1), rs.getLong(2), Long::sum);
        });
        return m;
    }
}
