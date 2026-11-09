package com.ritech.exports.logistica;

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
@Table(name = "transportistas")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Transportista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transportista")
    private Integer idTransportista;

    private String nombre;

    /** NAVIERA, AEREA o TERRESTRE. */
    private String tipo;

    private String contacto;
}
