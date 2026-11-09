package com.ritech.exports.logistica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "envios")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_envio")
    private Integer idEnvio;

    @Column(name = "id_orden")
    private Integer idOrden;

    @Column(name = "id_transportista")
    private Integer idTransportista;

    /** ISO 6346: 4 letras + 7 digitos (chk_contenedor_iso6346). */
    @Column(name = "numero_contenedor")
    private String numeroContenedor;

    @Column(name = "booking_bl")
    private String bookingBl;

    @Column(name = "puerto_origen")
    private String puertoOrigen;

    @Column(name = "puerto_destino")
    private String puertoDestino;

    @Column(name = "fecha_estimada_zarpe")
    private LocalDate fechaEstimadaZarpe;

    @Column(name = "fecha_estimada_arribo")
    private LocalDate fechaEstimadaArribo;

    @Column(name = "estado_envio")
    private String estadoEnvio;
}
