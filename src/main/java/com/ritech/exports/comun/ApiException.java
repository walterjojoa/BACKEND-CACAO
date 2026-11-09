package com.ritech.exports.comun;

import org.springframework.http.HttpStatus;

/** Error de negocio que el backend detecta antes de llegar a la base. */
public class ApiException extends RuntimeException {

    private final HttpStatus estado;

    public ApiException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public static ApiException noEncontrado(String que) {
        return new ApiException(HttpStatus.NOT_FOUND, que + " no existe.");
    }

    public static ApiException conflicto(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, mensaje);
    }

    public static ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    public static ApiException prohibido(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, mensaje);
    }
}
