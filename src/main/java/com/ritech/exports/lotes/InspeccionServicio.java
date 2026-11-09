package com.ritech.exports.lotes;

import com.ritech.exports.comun.ApiException;
import com.ritech.exports.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-17 registrar inspeccion; SCRUM-18 aprobar o rechazar con observacion obligatoria. */
@Service
@Transactional
public class InspeccionServicio {

    /** Estados que trg_insp_estado_ai sabe mover; fuera de ellos la inspeccion no cambiaria nada. */
    private static final Set<String> INSPECCIONABLE =
            Set.of(LoteExportacion.REGISTRADO, LoteExportacion.EN_INSPECCION, LoteExportacion.RECHAZADO);

    public record InspeccionSolicitud(
            @PastOrPresent LocalDate fechaInspeccion,
            @DecimalMin("0") @DecimalMax("100") BigDecimal humedadPorcentaje,
            @Min(0) Integer defectosPor300g,
            @DecimalMin("0") @DecimalMax("100") BigDecimal puntajeTaza,
            @DecimalMin("0") BigDecimal ocratoxinaPpb,
            @DecimalMin("0") BigDecimal aflatoxinasPpb,
            @NotBlank @Pattern(regexp = "APROBADO|RECHAZADO|CONDICIONADO") String resultado,
            String observaciones) {
    }

    public record InspeccionRegistrada(InspeccionCalidad inspeccion, String estadoLote) {
    }

    @PersistenceContext
    private EntityManager em;

    private final LoteServicio lotes;
    private final InspeccionRepositorio inspecciones;

    public InspeccionServicio(LoteServicio lotes, InspeccionRepositorio inspecciones) {
        this.lotes = lotes;
        this.inspecciones = inspecciones;
    }

    @Transactional(readOnly = true)
    public List<InspeccionCalidad> deLote(Integer idLote) {
        lotes.obtener(idLote);
        return inspecciones.findByIdLoteOrderByFechaInspeccionDescIdInspeccionDesc(idLote);
    }

    public InspeccionRegistrada registrar(Integer idLote, InspeccionSolicitud s) {
        LoteExportacion lote = lotes.obtener(idLote);
        if (!INSPECCIONABLE.contains(lote.getEstadoLote())) {
            throw ApiException.invalido("No se puede inspeccionar un lote en estado " + lote.getEstadoLote() + ".");
        }
        String obs = s.observaciones() == null ? null : s.observaciones().trim();
        // chk_resultado_insp exige lo mismo; aqui se da el mensaje claro (CP-024).
        if ("RECHAZADO".equals(s.resultado()) && (obs == null || obs.length() < 20)) {
            throw ApiException.invalido("Para rechazar un lote la observacion es obligatoria (minimo 20 caracteres).");
        }
        InspeccionCalidad i = new InspeccionCalidad();
        i.setIdLote(idLote);
        i.setIdUsuarioInspector(UsuarioActual.actual().idUsuario());
        i.setFechaInspeccion(s.fechaInspeccion());
        i.setHumedadPorcentaje(s.humedadPorcentaje());
        i.setDefectosPor300g(s.defectosPor300g());
        i.setPuntajeTaza(s.puntajeTaza());
        i.setOcratoxinaPpb(s.ocratoxinaPpb());
        i.setAflatoxinasPpb(s.aflatoxinasPpb());
        i.setResultado(s.resultado());
        i.setObservaciones(obs == null || obs.isEmpty() ? null : obs);
        inspecciones.saveAndFlush(i);
        em.refresh(i);
        em.refresh(lote); // trg_insp_estado_ai acaba de mover el estado
        return new InspeccionRegistrada(i, lote.getEstadoLote());
    }
}
