package com.ritech.exports.comun;

import java.time.OffsetDateTime;
import java.util.Map;

public record RespuestaError(
        int estado,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> campos,
        OffsetDateTime fecha) {
}
