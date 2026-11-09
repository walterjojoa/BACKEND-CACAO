package com.ritech.exports.ordenes;

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

/**
 * Tabla puente R19 ordenes <-> lotes. La compuerta de cumplimiento esta en trg_ordlot_bi:
 * lote APROBADO, dictamen CUMPLE vigente, importador valido (R-UE-008) y kg disponibles.
 */
@Entity
@Table(name = "ordenes_lotes")
@IdClass(OrdenLote.Clave.class)
@DynamicUpdate
@Getter
@Setter
public class OrdenLote {

    @Id
    @Column(name = "id_orden")
    private Integer idOrden;

    @Id
    @Column(name = "id_lote")
    private Integer idLote;

    @Column(name = "kg_asignados")
    private BigDecimal kgAsignados;

    /** En la moneda de la orden (EUR o USD), ver C-13. */
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;

    public static class Clave implements Serializable {
        private Integer idOrden;
        private Integer idLote;

        public Clave() {
        }

        public Clave(Integer idOrden, Integer idLote) {
            this.idOrden = idOrden;
            this.idLote = idLote;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(idOrden, c.idOrden) && Objects.equals(idLote, c.idLote);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idOrden, idLote);
        }
    }
}
