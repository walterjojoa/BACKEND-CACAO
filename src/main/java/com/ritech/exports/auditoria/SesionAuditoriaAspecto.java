package com.ritech.exports.auditoria;

import com.ritech.exports.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Los triggers de auditoria (trg_aud_*) leen el autor de las variables de sesion
 * de MySQL @app_id_usuario y @app_ip_origen (ver C-07 del script).
 *
 * Con un pool de conexiones esas variables sobreviven entre peticiones, asi que:
 *  1. se fijan al empezar cada transaccion de escritura de un servicio,
 *  2. se hace flush para que los triggers corran mientras siguen fijadas,
 *  3. se limpian al terminar, para que la siguiente peticion no herede el autor.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SesionAuditoriaAspecto {

    private static final Logger log = LoggerFactory.getLogger(SesionAuditoriaAspecto.class);

    @PersistenceContext
    private EntityManager em;

    @Around("within(com.ritech.exports..*) && "
            + "(@within(org.springframework.transaction.annotation.Transactional) "
            + "|| @annotation(org.springframework.transaction.annotation.Transactional)) "
            + "&& within(@org.springframework.stereotype.Service *)")
    public Object fijarAutor(ProceedingJoinPoint pjp) throws Throwable {
        boolean escritura = TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        // Si ya hay una transaccion con autor fijado (llamada anidada), no se repite.
        if (!escritura || TransactionSynchronizationManager.hasResource(SesionAuditoriaAspecto.class)) {
            return pjp.proceed();
        }
        UsuarioActual u = UsuarioActual.actualONulo();
        em.createNativeQuery("SET @app_id_usuario = ?1, @app_ip_origen = ?2")
                .setParameter(1, u == null ? null : u.idUsuario())
                .setParameter(2, ipOrigen())
                .executeUpdate();
        TransactionSynchronizationManager.bindResource(SesionAuditoriaAspecto.class, Boolean.TRUE);
        try {
            Object resultado = pjp.proceed();
            em.flush();
            return resultado;
        } finally {
            TransactionSynchronizationManager.unbindResource(SesionAuditoriaAspecto.class);
            try {
                em.createNativeQuery("SET @app_id_usuario = NULL, @app_ip_origen = NULL").executeUpdate();
            } catch (RuntimeException e) {
                log.warn("No se pudieron limpiar las variables de auditoria: {}", e.getMessage());
            }
        }
    }

    private static String ipOrigen() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String reenviada = req.getHeader("X-Forwarded-For");
            String ip = reenviada != null && !reenviada.isBlank() ? reenviada.split(",")[0].trim() : req.getRemoteAddr();
            return ip.length() > 45 ? ip.substring(0, 45) : ip;
        }
        return null;
    }
}
