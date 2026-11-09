package com.ritech.exports.cumplimiento;

import com.ritech.exports.lotes.LoteServicio;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-22: motor de validacion normativa. Toda la logica esta en sp_validar_lote;
 * aqui solo se invoca y se presenta el dictamen de la ultima corrida.
 */
@Service
@Transactional
public class ValidacionServicio {

    public record DetalleRegla(String codigoRegla, String normativa, String parametro, String operador,
                               String valorLimite, String valorEvaluado, String resultado, String mensaje) {
    }

    public record Dictamen(Integer idLote, String codigoLote, String estadoLote, LocalDateTime ultimaValidacion,
                           int reglasEvaluadas, int reglasIncumplidas, String dictamen, List<DetalleRegla> detalle) {
    }

    private final JdbcClient jdbc;
    private final LoteServicio lotes;

    public ValidacionServicio(JdbcClient jdbc, LoteServicio lotes) {
        this.jdbc = jdbc;
        this.lotes = lotes;
    }

    public Dictamen validar(Integer idLote) {
        lotes.obtener(idLote);
        // El procedimiento devuelve un resumen; se descarta y se lee la vista, que es la fuente unica.
        jdbc.sql("CALL sp_validar_lote(?)").param(idLote).query().listOfRows();
        return dictamen(idLote);
    }

    @Transactional(readOnly = true)
    public Dictamen dictamen(Integer idLote) {
        lotes.obtener(idLote);
        List<DetalleRegla> detalle = jdbc.sql("""
                        select r.codigo_regla, r.normativa_referencia, r.parametro,
                               v.operador_aplicado, v.valor_limite_aplicado, v.valor_evaluado, v.resultado,
                               case when v.resultado = 'NO_CUMPLE' then r.mensaje_error end
                          from validaciones_lote v
                          join reglas_normativas_ue r on r.id_regla = v.id_regla
                         where v.id_lote = ?
                           and v.fecha_ejecucion = (select max(fecha_ejecucion) from validaciones_lote where id_lote = ?)
                         order by r.codigo_regla""")
                .params(idLote, idLote)
                .query((rs, n) -> new DetalleRegla(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)))
                .list();
        return jdbc.sql("""
                        select id_lote, codigo_lote, estado_lote, ultima_validacion,
                               reglas_evaluadas, reglas_incumplidas, dictamen
                          from v_dictamen_lote where id_lote = ?""")
                .param(idLote)
                .query((rs, n) -> new Dictamen(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, LocalDateTime.class), rs.getInt(5), rs.getInt(6), rs.getString(7), detalle))
                .single();
    }
}
