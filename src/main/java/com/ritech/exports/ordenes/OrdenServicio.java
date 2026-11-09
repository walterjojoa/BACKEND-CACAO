package com.ritech.exports.ordenes;

import com.ritech.exports.comercial.ClienteRepositorio;
import com.ritech.exports.comercial.ClienteUe;
import com.ritech.exports.comun.ApiException;
import com.ritech.exports.cumplimiento.ValidacionServicio;
import com.ritech.exports.lotes.LoteExportacion;
import com.ritech.exports.lotes.LoteServicio;
import com.ritech.exports.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-25 crear orden; SCRUM-26 asignar lotes aprobados controlando kg disponibles. */
@Service
@Transactional
public class OrdenServicio {

    /** Transiciones permitidas de estado_orden. */
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            OrdenExportacion.BORRADOR, Set.of(OrdenExportacion.CONFIRMADA, OrdenExportacion.ANULADA),
            OrdenExportacion.CONFIRMADA, Set.of(OrdenExportacion.EN_TRANSITO, OrdenExportacion.ANULADA,
                    OrdenExportacion.BORRADOR),
            OrdenExportacion.EN_TRANSITO, Set.of(OrdenExportacion.ENTREGADA),
            OrdenExportacion.ENTREGADA, Set.of(),
            OrdenExportacion.ANULADA, Set.of());

    public record OrdenSolicitud(
            @NotNull Integer idCliente,
            LocalDate fechaOrden,
            @NotBlank @Pattern(regexp = "FOB|CIF|DAP|EXW|FCA|CFR|CPT|DDP") String incoterm,
            @Pattern(regexp = "EUR|USD") String moneda) {
    }

    public record AsignacionSolicitud(
            @NotNull Integer idLote,
            /** Opcional: por defecto todo el peso neto del lote. */
            @Positive BigDecimal kgAsignados,
            @NotNull @Positive BigDecimal precioUnitario) {
    }

    public record AsignacionEditar(@NotNull @Positive BigDecimal kgAsignados,
                                   @NotNull @Positive BigDecimal precioUnitario) {
    }

    public record CambioEstado(@NotBlank String estado) {
    }

    public record Detalle(OrdenExportacion orden, ClienteUe cliente, List<OrdenLote> lotes) {
    }

    @PersistenceContext
    private EntityManager em;

    private final OrdenRepositorio ordenes;
    private final OrdenLoteRepositorio asignaciones;
    private final ClienteRepositorio clientes;
    private final LoteServicio lotes;
    private final ValidacionServicio validacion;

    public OrdenServicio(OrdenRepositorio ordenes, OrdenLoteRepositorio asignaciones, ClienteRepositorio clientes,
                         LoteServicio lotes, ValidacionServicio validacion) {
        this.ordenes = ordenes;
        this.asignaciones = asignaciones;
        this.clientes = clientes;
        this.lotes = lotes;
        this.validacion = validacion;
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public List<OrdenExportacion> buscar(Integer idCliente, String estado) {
        UsuarioActual u = UsuarioActual.actual();
        Integer filtroCliente = u.esCliente() ? u.idCliente() : idCliente;
        return ordenes.buscar(filtroCliente, estado == null || estado.isBlank() ? null : estado.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public OrdenExportacion obtener(Integer id) {
        OrdenExportacion o = ordenes.findById(id).orElseThrow(() -> ApiException.noEncontrado("La orden"));
        UsuarioActual u = UsuarioActual.actualONulo();
        if (u != null && u.esCliente() && !o.getIdCliente().equals(u.idCliente())) {
            throw ApiException.noEncontrado("La orden");
        }
        return o;
    }

    @Transactional(readOnly = true)
    public Detalle detalle(Integer id) {
        OrdenExportacion o = obtener(id);
        return new Detalle(o, clientes.findById(o.getIdCliente()).orElse(null), asignaciones.findByIdOrden(id));
    }

    // ---------- Orden ----------

    public OrdenExportacion crear(OrdenSolicitud s) {
        ClienteUe c = clientes.findById(s.idCliente())
                .orElseThrow(() -> ApiException.invalido("El cliente no existe."));
        if (!"ACTIVO".equals(c.getEstado())) {
            throw ApiException.invalido("No se puede crear una orden para un cliente inactivo.");
        }
        OrdenExportacion o = new OrdenExportacion();
        o.setNumeroOrden(siguienteNumero());
        o.setIdCliente(c.getIdCliente());
        o.setIdUsuarioGestor(UsuarioActual.actual().idUsuario());
        o.setFechaOrden(s.fechaOrden() == null ? LocalDate.now() : s.fechaOrden());
        o.setIncoterm(s.incoterm());
        o.setMoneda(s.moneda() == null ? "EUR" : s.moneda());
        o.setEstadoOrden(OrdenExportacion.BORRADOR);
        ordenes.saveAndFlush(o);
        em.refresh(o);
        return o;
    }

    public OrdenExportacion editar(Integer id, OrdenSolicitud s) {
        OrdenExportacion o = enBorrador(id);
        if (!o.getIdCliente().equals(s.idCliente()) && !asignaciones.findByIdOrden(id).isEmpty()) {
            throw ApiException.invalido("Retire los lotes antes de cambiar el cliente de la orden.");
        }
        if (!clientes.existsById(s.idCliente())) {
            throw ApiException.invalido("El cliente no existe.");
        }
        o.setIdCliente(s.idCliente());
        if (s.fechaOrden() != null) {
            o.setFechaOrden(s.fechaOrden());
        }
        o.setIncoterm(s.incoterm());
        if (s.moneda() != null) {
            o.setMoneda(s.moneda());
        }
        return ordenes.saveAndFlush(o);
    }

    public OrdenExportacion cambiarEstado(Integer id, String nuevo) {
        OrdenExportacion o = obtener(id);
        String destino = nuevo.trim().toUpperCase();
        if (!TRANSICIONES.getOrDefault(o.getEstadoOrden(), Set.of()).contains(destino)) {
            throw ApiException.invalido("No se puede pasar una orden de " + o.getEstadoOrden() + " a " + destino + ".");
        }
        if (OrdenExportacion.CONFIRMADA.equals(destino) && asignaciones.findByIdOrden(id).isEmpty()) {
            throw ApiException.invalido("Una orden sin lotes no se puede confirmar.");
        }
        if (OrdenExportacion.ANULADA.equals(destino)) {
            // Borrar las asignaciones dispara trg_ordlot_ad, que devuelve cada lote a APROBADO.
            asignaciones.deleteAll(asignaciones.findByIdOrden(id));
            asignaciones.flush();
        }
        o.setEstadoOrden(destino);
        ordenes.saveAndFlush(o);
        if (OrdenExportacion.EN_TRANSITO.equals(destino)) {
            ordenes.marcarLotesExportados(id);
        }
        em.refresh(o);
        return o;
    }

    // ---------- Lotes de la orden ----------

    public Detalle asignar(Integer idOrden, AsignacionSolicitud s) {
        enBorrador(idOrden);
        LoteExportacion lote = lotes.obtener(s.idLote());
        if (!LoteExportacion.APROBADO.equals(lote.getEstadoLote())) {
            throw ApiException.invalido("Solo pueden asignarse lotes en estado APROBADO (el lote esta "
                    + lote.getEstadoLote() + ").");
        }
        // Se revalida justo antes de asignar: asi el dictamen que mira trg_ordlot_bi refleja la ultima
        // inspeccion y los certificados vigentes hoy, no una corrida vieja.
        ValidacionServicio.Dictamen d = validacion.validar(lote.getIdLote());
        if (!"CUMPLE".equals(d.dictamen())) {
            throw ApiException.invalido("El lote tiene dictamen " + d.dictamen() + " ("
                    + d.reglasIncumplidas() + " regla(s) incumplida(s)). Consulte /api/lotes/"
                    + lote.getIdLote() + "/dictamen.");
        }
        OrdenLote a = new OrdenLote();
        a.setIdOrden(idOrden);
        a.setIdLote(lote.getIdLote());
        a.setKgAsignados(s.kgAsignados() == null ? lote.getPesoNetoKg() : s.kgAsignados());
        a.setPrecioUnitario(s.precioUnitario());
        em.persist(a);
        em.flush(); // trg_ordlot_bi valida, trg_ordlot_ai recalcula valor_total y pasa el lote a ASIGNADO
        return refrescado(idOrden);
    }

    public Detalle editarAsignacion(Integer idOrden, Integer idLote, AsignacionEditar s) {
        enBorrador(idOrden);
        OrdenLote a = asignacion(idOrden, idLote);
        a.setKgAsignados(s.kgAsignados());
        a.setPrecioUnitario(s.precioUnitario());
        asignaciones.saveAndFlush(a);
        return refrescado(idOrden);
    }

    public Detalle quitar(Integer idOrden, Integer idLote) {
        enBorrador(idOrden);
        asignaciones.delete(asignacion(idOrden, idLote));
        asignaciones.flush();
        return refrescado(idOrden);
    }

    // ---------- Apoyo ----------

    private OrdenExportacion enBorrador(Integer id) {
        OrdenExportacion o = obtener(id);
        if (!OrdenExportacion.BORRADOR.equals(o.getEstadoOrden())) {
            throw ApiException.invalido("Solo se modifica una orden en BORRADOR (esta " + o.getEstadoOrden() + ").");
        }
        return o;
    }

    private OrdenLote asignacion(Integer idOrden, Integer idLote) {
        return asignaciones.findById(new OrdenLote.Clave(idOrden, idLote))
                .orElseThrow(() -> ApiException.noEncontrado("El lote en esta orden"));
    }

    private Detalle refrescado(Integer idOrden) {
        OrdenExportacion o = obtener(idOrden);
        em.refresh(o);
        return new Detalle(o, clientes.findById(o.getIdCliente()).orElse(null), asignaciones.findByIdOrden(idOrden));
    }

    private String siguienteNumero() {
        String prefijo = "ORD-" + LocalDate.now().getYear() + "-";
        String numero;
        long n = ordenes.ultimoConsecutivo(prefijo);
        do {
            numero = prefijo + String.format("%04d", ++n);
        } while (ordenes.existsByNumeroOrden(numero));
        return numero;
    }
}
