package com.ritech.exports;

import com.ritech.exports.lotes.LoteExportacion;
import com.ritech.exports.ordenes.OrdenExportacion;
import com.ritech.exports.seguridad.UsuarioActual;
import com.ritech.exports.usuarios.Rol;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** Utilidades de prueba: usuario autenticado y objetos con los campos que solo escribe la base. */
public final class Sesion {

    private Sesion() {
    }

    public static UsuarioActual como(String rol, int idUsuario, Integer idCliente, String... permisos) {
        UsuarioActual u = new UsuarioActual(idUsuario, rol.toLowerCase() + "@ritech.co", rol, idCliente, Set.of(permisos));
        List<SimpleGrantedAuthority> autoridades = new java.util.ArrayList<>(
                u.permisos().stream().map(SimpleGrantedAuthority::new).toList());
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, autoridades));
        return u;
    }

    public static UsuarioActual gestor() {
        return como(Rol.GESTOR, 2, null, "LOTES_CREAR", "LOTES_CONSULTAR", "ORDENES_GESTIONAR");
    }

    public static UsuarioActual cliente(int idCliente) {
        return como(Rol.CLIENTE_UE, 4, idCliente, "LOTES_CONSULTAR");
    }

    public static void limpiar() {
        SecurityContextHolder.clearContext();
    }

    public static LoteExportacion lote(int id, String estado, String pesoNeto) {
        LoteExportacion l = new LoteExportacion();
        l.setIdLote(id);
        l.setCodigoLote("CAF-2026-" + String.format("%04d", id));
        l.setIdProducto(1);
        l.setPesoNetoKg(new BigDecimal(pesoNeto));
        l.setPesoBrutoKg(new BigDecimal(pesoNeto).add(BigDecimal.TEN));
        l.setFechaCosecha(LocalDate.now().minusDays(30));
        ReflectionTestUtils.setField(l, "estadoLote", estado);
        return l;
    }

    public static OrdenExportacion orden(int id, int idCliente, String estado) {
        OrdenExportacion o = new OrdenExportacion();
        o.setIdOrden(id);
        o.setNumeroOrden("ORD-2026-" + String.format("%04d", id));
        o.setIdCliente(idCliente);
        o.setIdUsuarioGestor(2);
        o.setIncoterm("FOB");
        o.setMoneda("EUR");
        o.setEstadoOrden(estado);
        return o;
    }
}
