package com.ritech.exports.config;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Salud")
public class SaludControlador {

    private final JdbcClient jdbc;

    public SaludControlador(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/salud")
    public Map<String, Object> salud() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("servicio", "ritech-exports");
        r.put("version", "0.1.0");
        try {
            r.put("mysql", jdbc.sql("select version()").query(String.class).single());
            r.put("baseDeDatos", "ok");
        } catch (RuntimeException e) {
            r.put("baseDeDatos", "sin conexion");
        }
        return r;
    }
}
