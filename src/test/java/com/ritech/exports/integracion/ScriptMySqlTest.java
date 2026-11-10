package com.ritech.exports.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El lector del script no necesita MySQL: se comprueba que corta bien el archivo real. */
class ScriptMySqlTest {

    @Test
    void entiendeDelimiterYComentarios() {
        List<String> s = ScriptMySql.sentencias("""
                -- comentario de linea
                CREATE TABLE a (x INT);   -- comentario al final
                /* bloque con ; dentro
                   DELETE FROM a; */
                INSERT INTO a VALUES (1), ('--no es comentario');
                DELIMITER $$
                CREATE TRIGGER t BEFORE INSERT ON a FOR EACH ROW
                BEGIN
                  SET NEW.x = 1;
                END$$
                DELIMITER ;
                SELECT 1;
                """);

        assertThat(s).containsExactly(
                "CREATE TABLE a (x INT)",
                "INSERT INTO a VALUES (1), ('--no es comentario')",
                "CREATE TRIGGER t BEFORE INSERT ON a FOR EACH ROW\nBEGIN\n  SET NEW.x = 1;\nEND",
                "SELECT 1");
    }

    @Test
    void elScriptRealProduceLosObjetosEsperados() throws Exception {
        List<String> s = ScriptMySql.sentencias(Files.readString(Path.of("database", "db_ritech_exports.sql")));

        assertThat(s).filteredOn(x -> x.startsWith("CREATE TABLE")).hasSize(23);
        assertThat(s).filteredOn(x -> x.startsWith("CREATE TRIGGER")).hasSize(19);
        assertThat(s).filteredOn(x -> x.startsWith("CREATE PROCEDURE")).hasSize(2);
        assertThat(s).filteredOn(x -> x.startsWith("CREATE VIEW")).hasSize(3);
        assertThat(s).as("las pruebas negativas comentadas no se ejecutan")
                .noneMatch(x -> x.contains("CAF-2026-9901"));
        assertThat(s).noneMatch(x -> x.contains("DELIMITER"));
    }
}
