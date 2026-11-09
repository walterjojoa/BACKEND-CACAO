package com.ritech.exports;

import static org.assertj.core.api.Assertions.assertThat;

import com.ritech.exports.comun.ManejadorErrores;
import com.ritech.exports.comun.RespuestaError;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** Los mensajes de los triggers deben llegar al usuario tal cual y con el codigo HTTP correcto. */
class ManejadorErroresTest {

    private final ManejadorErrores manejador = new ManejadorErrores();

    @SuppressWarnings("unchecked")
    private ResponseEntity<RespuestaError> traducir(SQLException sql) throws Exception {
        Method m = ManejadorErrores.class.getDeclaredMethod("baseDeDatos", Exception.class, HttpServletRequest.class);
        m.setAccessible(true);
        return (ResponseEntity<RespuestaError>) m.invoke(manejador,
                new DataIntegrityViolationException("x", sql), new MockHttpServletRequest("POST", "/api/x"));
    }

    @Test
    void signalDeTriggerEs422ConSuMensaje() throws Exception {
        var r = traducir(new SQLException("Solo pueden asignarse lotes en estado APROBADO.", "45000", 1644));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody().mensaje()).isEqualTo("Solo pueden asignarse lotes en estado APROBADO.");
    }

    @Test
    void duplicadoEs409() throws Exception {
        var r = traducir(new SQLException(
                "Duplicate entry 'DE123456789012345' for key 'clientes_ue.uq_clientes_eori'", "23000", 1062));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().mensaje()).contains("DE123456789012345").contains("uq_clientes_eori");
    }

    @Test
    void checkVioladoEs400ConElNombreDeLaRestriccion() throws Exception {
        var r = traducir(new SQLException("Check constraint 'chk_pesos' is violated.", "HY000", 3819));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().mensaje()).contains("chk_pesos");
    }

    @Test
    void borrarConHijosEs409() throws Exception {
        var r = traducir(new SQLException("Cannot delete or update a parent row", "23000", 1451));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
    }
}
