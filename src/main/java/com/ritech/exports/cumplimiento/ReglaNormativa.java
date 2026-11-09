package com.ritech.exports.cumplimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** Catalogo administrable de normativa UE (SCRUM-21). */
@Entity
@Table(name = "reglas_normativas_ue")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class ReglaNormativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regla")
    private Integer idRegla;

    @Column(name = "codigo_regla")
    private String codigoRegla;

    @Column(name = "normativa_referencia")
    private String normativaReferencia;

    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(name = "aplica_tipo")
    private String aplicaTipo;

    /** LOTE: la evalua sp_validar_lote. ORDEN: la evalua trg_ordlot_bi al asignar. */
    private String ambito;

    private String parametro;
    private String operador;

    @Column(name = "valor_limite")
    private String valorLimite;

    @Column(name = "mensaje_error")
    private String mensajeError;

    private Boolean activa;
}
