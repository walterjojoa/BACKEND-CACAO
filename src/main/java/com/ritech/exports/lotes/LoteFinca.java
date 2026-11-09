package com.ritech.exports.lotes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabla puente R17 lotes <-> fincas: el nucleo de la trazabilidad EUDR. */
@Entity
@Table(name = "lotes_fincas")
@IdClass(LoteFinca.Clave.class)
@DynamicUpdate
@Getter
@Setter
public class LoteFinca {

    @Id
    @Column(name = "id_lote")
    private Integer idLote;

    @Id
    @Column(name = "id_finca")
    private Integer idFinca;

    @Column(name = "porcentaje_aporte")
    private BigDecimal porcentajeAporte;

    @Column(name = "kg_aportados")
    private BigDecimal kgAportados;

    public static class Clave implements Serializable {
        private Integer idLote;
        private Integer idFinca;

        public Clave() {
        }

        public Clave(Integer idLote, Integer idFinca) {
            this.idLote = idLote;
            this.idFinca = idFinca;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(idLote, c.idLote) && Objects.equals(idFinca, c.idFinca);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idLote, idFinca);
        }
    }
}
