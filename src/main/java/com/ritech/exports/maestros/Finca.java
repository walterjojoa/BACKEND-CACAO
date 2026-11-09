package com.ritech.exports.maestros;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** Finca con geolocalizacion. EUDR: si supera 4 ha exige poligono (chk_poligono_eudr). */
@Entity
@Table(name = "fincas")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Finca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_finca")
    private Integer idFinca;

    @Column(name = "id_productor")
    private Integer idProductor;

    @Column(name = "nombre_finca")
    private String nombreFinca;

    @Column(name = "area_hectareas")
    private BigDecimal areaHectareas;

    private BigDecimal latitud;
    private BigDecimal longitud;

    /** GeoJSON tal cual (columna JSON de MySQL). */
    @JsonRawValue
    @Column(name = "poligono_geojson")
    private String poligonoGeojson;

    @Column(name = "fecha_verificacion_deforestacion")
    private LocalDate fechaVerificacionDeforestacion;
}
