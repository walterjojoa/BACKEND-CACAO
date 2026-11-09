package com.ritech.exports.lotes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Immutable;

/**
 * Inspeccion de calidad: es evidencia EUDR, por eso no se edita ni se borra
 * (una correccion es una nueva inspeccion, que ademas mueve el estado del lote).
 */
@Entity
@Immutable
@Table(name = "inspecciones_calidad")
@DynamicInsert
@Getter
@Setter
public class InspeccionCalidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inspeccion")
    private Integer idInspeccion;

    @Column(name = "id_lote")
    private Integer idLote;

    @Column(name = "id_usuario_inspector")
    private Integer idUsuarioInspector;

    @Column(name = "fecha_inspeccion")
    private LocalDate fechaInspeccion;

    @Column(name = "humedad_porcentaje")
    private BigDecimal humedadPorcentaje;

    @Column(name = "defectos_por_300g")
    private Integer defectosPor300g;

    @Column(name = "puntaje_taza")
    private BigDecimal puntajeTaza;

    @Column(name = "ocratoxina_ppb")
    private BigDecimal ocratoxinaPpb;

    @Column(name = "aflatoxinas_ppb")
    private BigDecimal aflatoxinasPpb;

    private String resultado;

    private String observaciones;
}
