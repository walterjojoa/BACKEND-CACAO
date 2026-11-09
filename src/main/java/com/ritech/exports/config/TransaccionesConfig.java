package com.ritech.exports.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * La transaccion debe abrirse ANTES que el aspecto de auditoria, para que el
 * SET @app_id_usuario se ejecute sobre la misma conexion que luego usan los triggers.
 * Por eso el interceptor transaccional va con la maxima precedencia (capa externa).
 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class TransaccionesConfig {
}
