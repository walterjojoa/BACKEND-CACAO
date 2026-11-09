package com.ritech.exports;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranca JPA y todos los repositorios: si una entidad esta mal mapeada o una consulta
 * JPQL (@Query) no compila, este test falla. No crea tablas ni toca MySQL.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MapeoJpaTest {

    @Autowired
    EntityManager em;

    @Test
    void lasEntidadesCubrenLasTablasDelScript() {
        Set<String> entidades = em.getMetamodel().getEntities().stream()
                .map(EntityType::getName).collect(Collectors.toSet());
        // Tablas del script con entidad propia. roles_permisos es el @ManyToMany de Rol;
        // validaciones_lote y documentos_exportacion se leen/escriben por SQL (procedimiento y vista).
        assertThat(entidades).containsExactlyInAnyOrder(
                "Rol", "Permiso", "Usuario", "Auditoria",
                "Productor", "Finca", "Producto",
                "LoteExportacion", "LoteFinca", "InspeccionCalidad",
                "Certificacion", "LoteCertificacion", "ReglaNormativa",
                "Pais", "ClienteUe", "OrdenExportacion", "OrdenLote",
                "Transportista", "Envio", "SeguimientoEnvio");
    }
}
