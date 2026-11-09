package com.ritech.exports.auditoria;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Solo lectura: la tabla la llenan los triggers trg_aud_*. */
@Entity
@Immutable
@Table(name = "auditoria")
@Getter
public class Auditoria {

    @Id
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "tabla_afectada")
    private String tablaAfectada;

    @Column(name = "id_registro")
    private String idRegistro;

    private String accion;

    @JsonRawValue
    @Column(name = "valor_anterior")
    private String valorAnterior;

    @JsonRawValue
    @Column(name = "valor_nuevo")
    private String valorNuevo;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "ip_origen")
    private String ipOrigen;
}
