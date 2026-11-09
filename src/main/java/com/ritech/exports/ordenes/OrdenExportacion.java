package com.ritech.exports.ordenes;

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
import org.hibernate.annotations.DynamicUpdate;

/** valor_total lo calcula la base (trg_ordlot_ai/au/ad); el backend nunca lo escribe. */
@Entity
@Table(name = "ordenes_exportacion")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class OrdenExportacion {

    public static final String BORRADOR = "BORRADOR";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String EN_TRANSITO = "EN_TRANSITO";
    public static final String ENTREGADA = "ENTREGADA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden")
    private Integer idOrden;

    @Column(name = "numero_orden")
    private String numeroOrden;

    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "id_usuario_gestor")
    private Integer idUsuarioGestor;

    @Column(name = "fecha_orden")
    private LocalDate fechaOrden;

    private String incoterm;
    private String moneda;

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "valor_total", insertable = false, updatable = false)
    private BigDecimal valorTotal;

    @Column(name = "estado_orden")
    private String estadoOrden;
}
