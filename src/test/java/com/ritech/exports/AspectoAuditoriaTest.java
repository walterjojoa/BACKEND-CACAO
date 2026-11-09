package com.ritech.exports;

import static org.assertj.core.api.Assertions.assertThat;

import com.ritech.exports.auditoria.SesionAuditoriaAspecto;
import com.ritech.exports.comercial.ClienteServicio;
import com.ritech.exports.ordenes.OrdenControlador;
import com.ritech.exports.seguridad.AuthControlador;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

/** El aspecto que fija @app_id_usuario debe envolver los servicios transaccionales y nada mas. */
class AspectoAuditoriaTest {

    private static AspectJExpressionPointcut pointcut() throws Exception {
        Method m = SesionAuditoriaAspecto.class.getMethod("fijarAutor", ProceedingJoinPoint.class);
        AspectJExpressionPointcut p = new AspectJExpressionPointcut();
        p.setExpression(m.getAnnotation(Around.class).value());
        return p;
    }

    @Test
    void envuelveLosMetodosDeLosServicios() throws Exception {
        Method crear = ClienteServicio.class.getMethod("crear", ClienteServicio.ClienteSolicitud.class);
        assertThat(pointcut().matches(crear, ClienteServicio.class)).isTrue();
    }

    @Test
    void noEnvuelveControladores() throws Exception {
        Method login = AuthControlador.class.getMethod("login", AuthControlador.LoginSolicitud.class);
        Method listar = OrdenControlador.class.getMethod("buscar", Integer.class, String.class);
        assertThat(pointcut().matches(login, AuthControlador.class)).isFalse();
        assertThat(pointcut().matches(listar, OrdenControlador.class)).isFalse();
    }
}
