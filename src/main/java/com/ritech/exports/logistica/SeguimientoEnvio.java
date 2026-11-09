package com.ritech.exports.logistica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Immutable;

/** Bitacora inmutable de eventos del envio (sin updated_at a proposito). */
@Entity
@Immutable
@Table(name = "seguimiento_envios")
@DynamicInsert
@Getter
@Setter
public class SeguimientoEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_seguimiento")
    private Integer idSeguimiento;

    @Column(name = "id_envio")
    private Integer idEnvio;

    @Column(name = "fecha_evento")
    private LocalDateTime fechaEvento;

    private String ubicacion;
    private String estado;
    private String observacion;
}
