package com.ritech.exports.integracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecuta database/db_ritech_exports.sql tal como lo hace MySQL Workbench: entiende DELIMITER,
 * ignora comentarios y corre cada sentencia por separado. El nombre de la base se cambia por
 * uno de pruebas para no tocar nunca la base de trabajo.
 */
final class ScriptMySql {

    static final String BASE_PRUEBAS = "db_ritech_pruebas";

    private ScriptMySql() {
    }

    static void cargar(String urlServidor, String usuario, String clave) throws IOException, SQLException {
        String script = Files.readString(Path.of("database", "db_ritech_exports.sql"), StandardCharsets.UTF_8)
                .replace("db_ritech_exports", BASE_PRUEBAS);
        try (Connection c = DriverManager.getConnection(urlServidor, usuario, clave);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BASE_PRUEBAS);
            for (String sentencia : sentencias(script)) {
                try {
                    st.execute(sentencia);
                } catch (SQLException e) {
                    throw new SQLException("Fallo el script en:\n" + sentencia + "\n-> " + e.getMessage(), e);
                }
            }
        }
    }

    static List<String> sentencias(String script) {
        String sinBloques = script.replaceAll("(?s)/\\*.*?\\*/", "");
        List<String> salida = new ArrayList<>();
        String delimitador = ";";
        StringBuilder actual = new StringBuilder();
        for (String cruda : sinBloques.split("\\R")) {
            String linea = sinComentario(cruda);
            String recortada = linea.trim();
            if (recortada.toUpperCase().startsWith("DELIMITER ")) {
                delimitador = recortada.substring("DELIMITER ".length()).trim();
                continue;
            }
            if (recortada.isEmpty()) {
                continue;
            }
            actual.append(linea).append('\n');
            if (recortada.endsWith(delimitador)) {
                String s = actual.toString().trim();
                s = s.substring(0, s.length() - delimitador.length()).trim();
                if (!s.isEmpty()) {
                    salida.add(s);
                }
                actual.setLength(0);
            }
        }
        if (!actual.toString().isBlank()) {
            salida.add(actual.toString().trim());
        }
        return salida;
    }

    /** Quita un comentario "-- ..." que no este dentro de una cadena entre comillas simples. */
    private static String sinComentario(String linea) {
        boolean enCadena = false;
        for (int i = 0; i < linea.length() - 1; i++) {
            char ch = linea.charAt(i);
            if (ch == '\'') {
                enCadena = !enCadena;
            } else if (!enCadena && ch == '-' && linea.charAt(i + 1) == '-'
                    && (i + 2 == linea.length() || Character.isWhitespace(linea.charAt(i + 2)))) {
                return linea.substring(0, i);
            }
        }
        return linea;
    }
}
