package com.ritech.exports.comercial;

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
import org.hibernate.annotations.DynamicUpdate;

/**
 * Importador europeo. La base ya garantiza: pais miembro de la UE (trg_clientes_pais_*),
 * formato EORI (chk_eori_formato), EORI unico y auditoria automatica (trg_aud_clientes_*).
 */
@Entity
@Table(name = "clientes_ue")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class ClienteUe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "razon_social")
    private String razonSocial;

    @Column(name = "numero_eori")
    private String numeroEori;

    @Column(name = "nit_vat")
    private String nitVat;

    @Column(name = "id_pais")
    private Integer idPais;

    private String ciudad;
    private String direccion;

    @Column(name = "email_contacto")
    private String emailContacto;

    private String estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
