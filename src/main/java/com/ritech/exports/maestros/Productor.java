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
@Table(name = "productores")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Productor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_productor")
    private Integer idProductor;

    @Column(name = "nit_o_cedula")
    private String nitOCedula;

    private String nombre;
    private String municipio;
    private String departamento;
    private String telefono;
}
