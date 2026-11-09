package com.ritech.exports.lotes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Entidad nucleo. estado_lote lo mueve la base, no el backend:
 *  - trg_insp_estado_ai: REGISTRADO/EN_INSPECCION/RECHAZADO -> segun resultado de la inspeccion
 *  - trg_ordlot_ai / trg_ordlot_ad: APROBADO <-> ASIGNADO
 * Por eso no tiene setter publico de estado y se usa @DynamicUpdate (nunca se reescribe por accidente).
 */
@Entity
@Table(name = "lotes_exportacion")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class LoteExportacion {

    public static final String REGISTRADO = "REGISTRADO";
    public static final String EN_INSPECCION = "EN_INSPECCION";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String ASIGNADO = "ASIGNADO";
    public static final String EXPORTADO = "EXPORTADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Integer idLote;

    @Column(name = "codigo_lote")
    private String codigoLote;

    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(name = "id_usuario_registra")
    private Integer idUsuarioRegistra;

    @Column(name = "peso_neto_kg")
    private BigDecimal pesoNetoKg;

    @Column(name = "peso_bruto_kg")
    private BigDecimal pesoBrutoKg;

    @Column(name = "fecha_cosecha")
    private LocalDate fechaCosecha;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "estado_lote", insertable = false, updatable = false)
    private String estadoLote;
}
