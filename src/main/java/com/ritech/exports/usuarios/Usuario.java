package com.ritech.exports.usuarios;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "usuarios")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_rol")
    private Rol rol;

    /** Solo para el rol CLIENTE_UE: la ficha comercial a la que pertenece la cuenta. */
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "nombre_completo")
    private String nombreCompleto;

    private String email;

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    private String estado;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;
}
