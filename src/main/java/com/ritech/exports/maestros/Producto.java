package com.ritech.exports.maestros;

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

@Entity
@Table(name = "productos")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    /** CAFE o CACAO: decide que reglas normativas le aplican al lote. */
    private String tipo;

    @Column(name = "nombre_comercial")
    private String nombreComercial;

    private String variedad;

    @Column(name = "codigo_hs")
    private String codigoHs;

    @Column(name = "unidad_medida")
    private String unidadMedida;
}
