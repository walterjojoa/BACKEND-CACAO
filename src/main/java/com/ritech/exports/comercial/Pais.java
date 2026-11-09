package com.ritech.exports.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Catalogo fijo cargado por el script (27 miembros de la UE + Colombia). */
@Entity
@Immutable
@Table(name = "paises")
@Getter
public class Pais {

    @Id
    @Column(name = "id_pais")
    private Integer idPais;

    @Column(name = "codigo_iso2")
    private String codigoIso2;

    private String nombre;

    @Column(name = "pertenece_ue")
    private Boolean perteneceUe;
}
