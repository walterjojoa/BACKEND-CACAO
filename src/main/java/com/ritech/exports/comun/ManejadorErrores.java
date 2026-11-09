package com.ritech.exports.comun;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Convierte los errores en respuestas JSON en espanol.
 *
 * Buena parte de las reglas del negocio viven en la base (triggers con SIGNAL 45000,
 * CHECK, UNIQUE, FK). Aqui se traduce cada familia de error de MySQL a un codigo HTTP,
 * conservando el mensaje que el trigger ya redacta para el usuario.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    private static final Pattern CHECK = Pattern.compile("Check constraint '([^']+)'");
    private static final Pattern DUPLICADO = Pattern.compile("Duplicate entry '([^']*)' for key '([^']+)'");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<RespuestaError> negocio(ApiException e, HttpServletRequest req) {
        return responder(e.getEstado(), e.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(f.getField(), f.getDefaultMessage());
        }
        return responder(HttpStatus.BAD_REQUEST, "Hay datos invalidos en la solicitud.", req, campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<RespuestaError> parametros(ConstraintViolationException e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), req, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<RespuestaError> formato(Exception e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo o los parametros no tienen el formato esperado.", req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<RespuestaError> credenciales(BadCredentialsException e, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, e.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<RespuestaError> acceso(AccessDeniedException e, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta operacion.", req, null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class,
            jakarta.persistence.PersistenceException.class})
    ResponseEntity<RespuestaError> baseDeDatos(Exception e, HttpServletRequest req) {
        SQLException sql = buscarSql(e);
        if (sql == null) {
            log.error("Error de persistencia sin SQLException", e);
            return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno de base de datos.", req, null);
        }
        String estadoSql = sql.getSQLState() == null ? "" : sql.getSQLState();
        String mensaje = sql.getMessage() == null ? "" : sql.getMessage();
        int codigo = sql.getErrorCode();

        // SIGNAL SQLSTATE '45000' de los triggers y procedimientos: el texto ya es para el usuario.
        if ("45000".equals(estadoSql)) {
            return responder(HttpStatus.UNPROCESSABLE_ENTITY, mensaje, req, null);
        }
        if (codigo == 3819) { // CHECK constraint violado
            Matcher m = CHECK.matcher(mensaje);
            String regla = m.find() ? m.group(1) : "desconocida";
            return responder(HttpStatus.BAD_REQUEST,
                    "El dato no cumple la restriccion " + regla + " de la base de datos.", req, null);
        }
        if (codigo == 1062) {
            Matcher m = DUPLICADO.matcher(mensaje);
            String detalle = m.find() ? " (valor '" + m.group(1) + "', clave " + m.group(2) + ")" : "";
            return responder(HttpStatus.CONFLICT, "Ya existe un registro con ese valor" + detalle + ".", req, null);
        }
        if (codigo == 1451) {
            return responder(HttpStatus.CONFLICT,
                    "No se puede eliminar: el registro tiene informacion asociada.", req, null);
        }
        if (codigo == 1452) {
            return responder(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Se hace referencia a un registro que no existe.", req, null);
        }
        if (codigo == 1048) {
            return responder(HttpStatus.BAD_REQUEST, "Falta un dato obligatorio: " + mensaje, req, null);
        }
        log.error("Error SQL no clasificado [{} / {}]", estadoSql, codigo, e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno de base de datos.", req, null);
    }

    private static SQLException buscarSql(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException s) {
                return s;
            }
            t = t.getCause();
        }
        return null;
    }

    private static ResponseEntity<RespuestaError> responder(HttpStatus estado, String mensaje,
                                                            HttpServletRequest req, Map<String, String> campos) {
        return ResponseEntity.status(estado).body(new RespuestaError(
                estado.value(), estado.getReasonPhrase(), mensaje, req.getRequestURI(), campos,
                OffsetDateTime.now()));
    }
}
