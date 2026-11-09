package com.ritech.exports.cumplimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** Tabla puente R18 lotes <-> certificaciones, con vigencia y soporte digitalizado. */
@Entity
@Table(name = "lotes_certificaciones")
@IdClass(LoteCertificacion.Clave.class)
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class LoteCertificacion {

    @Id
    @Column(name = "id_lote")
    private Integer idLote;

    @Id
    @Column(name = "id_certificacion")
    private Integer idCertificacion;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "url_soporte")
    private String urlSoporte;

    @Column(name = "estado_validacion")
    private String estadoValidacion;

    public static class Clave implements Serializable {
        private Integer idLote;
        private Integer idCertificacion;

        public Clave() {
        }

        public Clave(Integer idLote, Integer idCertificacion) {
            this.idLote = idLote;
            this.idCertificacion = idCertificacion;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(idLote, c.idLote)
                    && Objects.equals(idCertificacion, c.idCertificacion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idLote, idCertificacion);
        }
    }
}
