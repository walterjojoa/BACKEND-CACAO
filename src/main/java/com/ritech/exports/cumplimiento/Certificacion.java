package com.ritech.exports.cumplimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Catalogo de tipos de certificado. 'FITOSANITARIO' es literal: lo busca sp_validar_lote (R-UE-007). */
@Entity
@Immutable
@Table(name = "certificaciones")
@Getter
public class Certificacion {

    @Id
    @Column(name = "id_certificacion")
    private Integer idCertificacion;

    private String codigo;
    private String nombre;

    @Column(name = "entidad_emisora")
    private String entidadEmisora;

    @Column(name = "obligatoria_ue")
    private Boolean obligatoriaUe;
}
