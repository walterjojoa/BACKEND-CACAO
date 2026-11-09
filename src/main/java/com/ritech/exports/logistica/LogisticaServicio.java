package com.ritech.exports.logistica;

import com.ritech.exports.comun.ApiException;
import com.ritech.exports.ordenes.OrdenExportacion;
import com.ritech.exports.ordenes.OrdenServicio;
import com.ritech.exports.seguridad.UsuarioActual;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-28 programar envio, SCRUM-29 registrar seguimiento, SCRUM-30 consulta del cliente. */
@Service
@Transactional
public class LogisticaServicio {

    private static final String ESTADOS = "PROGRAMADO|EN_PUERTO|EN_TRANSITO|ARRIBADO|ENTREGADO|INCIDENCIA";

    /** Como avanza la orden cuando el envio reporta un evento. */
    private static final Map<String, String> ORDEN_SEGUN_ENVIO = Map.of(
            "EN_TRANSITO", OrdenExportacion.EN_TRANSITO,
            "ENTREGADO", OrdenExportacion.ENTREGADA);

    public record TransportistaSolicitud(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Pattern(regexp = "NAVIERA|AEREA|TERRESTRE") String tipo,
            @Size(max = 120) String contacto) {
    }

    public record EnvioSolicitud(
            @NotNull Integer idTransportista,
            @Pattern(regexp = "^[A-Z]{4}[0-9]{7}$", message = "ISO 6346: 4 letras mayusculas y 7 digitos")
            String numeroContenedor,
            @Size(max = 40) String bookingBl,
            @NotBlank @Size(max = 80) String puertoOrigen,
            @NotBlank @Size(max = 80) String puertoDestino,
            @FutureOrPresent LocalDate fechaEstimadaZarpe,
            LocalDate fechaEstimadaArribo) {
    }

    public record EventoSolicitud(
            @PastOrPresent LocalDateTime fechaEvento,
            @NotBlank @Size(max = 120) String ubicacion,
            @NotBlank @Pattern(regexp = ESTADOS) String estado,
            @Size(max = 300) String observacion) {
    }

    public record EnvioDetalle(Envio envio, String transportista, String numeroOrden,
                               List<SeguimientoEnvio> seguimiento) {
    }

    private final TransportistaRepositorio transportistas;
    private final EnvioRepositorio envios;
    private final SeguimientoRepositorio seguimiento;
    private final OrdenServicio ordenes;

    public LogisticaServicio(TransportistaRepositorio transportistas, EnvioRepositorio envios,
                             SeguimientoRepositorio seguimiento, OrdenServicio ordenes) {
        this.transportistas = transportistas;
        this.envios = envios;
        this.seguimiento = seguimiento;
        this.ordenes = ordenes;
    }

    // ---------- Transportistas ----------

    @Transactional(readOnly = true)
    public List<Transportista> transportistas() {
        return transportistas.findAllByOrderByNombreAsc();
    }

    public Transportista guardarTransportista(Integer id, TransportistaSolicitud s) {
        Transportista t = id == null ? new Transportista()
                : transportistas.findById(id).orElseThrow(() -> ApiException.noEncontrado("El transportista"));
        t.setNombre(s.nombre().trim());
        t.setTipo(s.tipo());
        t.setContacto(s.contacto() == null || s.contacto().isBlank() ? null : s.contacto().trim());
        return transportistas.saveAndFlush(t);
    }

    // ---------- Envios ----------

    @Transactional(readOnly = true)
    public List<Envio> buscar(String estado) {
        UsuarioActual u = UsuarioActual.actual();
        return envios.buscar(estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(),
                u.esCliente() ? u.idCliente() : null);
    }

    @Transactional(readOnly = true)
    public EnvioDetalle detalle(Integer idEnvio) {
        Envio e = envio(idEnvio);
        OrdenExportacion o = ordenes.obtener(e.getIdOrden()); // un cliente solo llega a sus propias ordenes
        String transportista = transportistas.findById(e.getIdTransportista()).map(Transportista::getNombre).orElse(null);
        return new EnvioDetalle(e, transportista, o.getNumeroOrden(),
                seguimiento.findByIdEnvioOrderByFechaEventoAscIdSeguimientoAsc(idEnvio));
    }

    public EnvioDetalle programar(Integer idOrden, EnvioSolicitud s) {
        OrdenExportacion o = ordenes.obtener(idOrden);
        if (!OrdenExportacion.CONFIRMADA.equals(o.getEstadoOrden())) {
            throw ApiException.invalido("Solo se programa el envio de una orden CONFIRMADA (esta "
                    + o.getEstadoOrden() + ").");
        }
        if (!transportistas.existsById(s.idTransportista())) {
            throw ApiException.invalido("El transportista no existe.");
        }
        if (s.fechaEstimadaZarpe() != null && s.fechaEstimadaArribo() != null
                && s.fechaEstimadaArribo().isBefore(s.fechaEstimadaZarpe())) {
            throw ApiException.invalido("El arribo estimado no puede ser anterior al zarpe.");
        }
        String bl = s.bookingBl() == null || s.bookingBl().isBlank() ? null : s.bookingBl().trim();
        if (bl != null && envios.existsByBookingBl(bl)) {
            throw ApiException.conflicto("Ya existe un envio con el booking/BL " + bl + ".");
        }
        Envio e = new Envio();
        e.setIdOrden(idOrden);
        e.setIdTransportista(s.idTransportista());
        e.setNumeroContenedor(s.numeroContenedor());
        e.setBookingBl(bl);
        e.setPuertoOrigen(s.puertoOrigen().trim());
        e.setPuertoDestino(s.puertoDestino().trim());
        e.setFechaEstimadaZarpe(s.fechaEstimadaZarpe());
        e.setFechaEstimadaArribo(s.fechaEstimadaArribo());
        e.setEstadoEnvio("PROGRAMADO");
        envios.saveAndFlush(e);
        registrarEvento(e.getIdEnvio(), new EventoSolicitud(null, e.getPuertoOrigen(), "PROGRAMADO",
                "Envio programado"));
        return detalle(e.getIdEnvio());
    }

    /** Cada evento queda en la bitacora y mueve el estado del envio y, si aplica, el de la orden. */
    public EnvioDetalle registrarEvento(Integer idEnvio, EventoSolicitud s) {
        Envio e = envio(idEnvio);
        if ("ENTREGADO".equals(e.getEstadoEnvio())) {
            throw ApiException.invalido("El envio ya fue entregado; no admite mas eventos.");
        }
        SeguimientoEnvio ev = new SeguimientoEnvio();
        ev.setIdEnvio(idEnvio);
        ev.setFechaEvento(s.fechaEvento());
        ev.setUbicacion(s.ubicacion().trim());
        ev.setEstado(s.estado());
        ev.setObservacion(s.observacion() == null || s.observacion().isBlank() ? null : s.observacion().trim());
        seguimiento.saveAndFlush(ev);

        e.setEstadoEnvio(s.estado());
        envios.saveAndFlush(e);

        String estadoOrden = ORDEN_SEGUN_ENVIO.get(s.estado());
        if (estadoOrden != null) {
            OrdenExportacion o = ordenes.obtener(e.getIdOrden());
            // Si el evento salta un paso (p. ej. ENTREGADO estando CONFIRMADA) se recorre la cadena.
            if (OrdenExportacion.ENTREGADA.equals(estadoOrden)
                    && OrdenExportacion.CONFIRMADA.equals(o.getEstadoOrden())) {
                ordenes.cambiarEstado(o.getIdOrden(), OrdenExportacion.EN_TRANSITO);
                o = ordenes.obtener(e.getIdOrden());
            }
            if (!estadoOrden.equals(o.getEstadoOrden())) {
                ordenes.cambiarEstado(o.getIdOrden(), estadoOrden);
            }
        }
        return detalle(idEnvio);
    }

    private Envio envio(Integer id) {
        return envios.findById(id).orElseThrow(() -> ApiException.noEncontrado("El envio"));
    }
}
