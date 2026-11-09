package com.ritech.exports.lotes;

import com.ritech.exports.comun.ApiException;
import com.ritech.exports.maestros.FincaRepositorio;
import com.ritech.exports.maestros.Producto;
import com.ritech.exports.maestros.ProductoRepositorio;
import com.ritech.exports.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-15 registrar lote, SCRUM-16 fincas de origen, SCRUM-19 trazabilidad. */
@Service
@Transactional
public class LoteServicio {

    /** Estados en que todavia se puede tocar el origen del lote (antes de quedar aprobado). */
    private static final Set<String> ORIGEN_EDITABLE =
            Set.of(LoteExportacion.REGISTRADO, LoteExportacion.EN_INSPECCION, LoteExportacion.RECHAZADO);

    public record LoteSolicitud(
            @Pattern(regexp = "^[A-Z0-9-]{3,30}$", message = "solo mayusculas, digitos y guiones (3 a 30)")
            String codigoLote,
            @NotNull Integer idProducto,
            @NotNull @Positive BigDecimal pesoNetoKg,
            @NotNull @Positive BigDecimal pesoBrutoKg,
            @NotNull @PastOrPresent LocalDate fechaCosecha) {
    }

    public record AporteSolicitud(
            @NotNull Integer idFinca,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal porcentajeAporte,
            /** Opcional: si no llega se calcula como peso neto x porcentaje. */
            @Positive BigDecimal kgAportados) {
    }

    public record AporteEditar(
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal porcentajeAporte,
            @Positive BigDecimal kgAportados) {
    }

    public record FilaTrazabilidad(String productor, String nombreFinca, BigDecimal areaHectareas,
                                   BigDecimal latitud, BigDecimal longitud, BigDecimal porcentajeAporte,
                                   BigDecimal kgAportados, String municipio, String departamento) {
    }

    public record Trazabilidad(String codigoLote, String producto, String codigoHs, BigDecimal pesoNetoKg,
                               String estadoLote, BigDecimal sumaPorcentaje, BigDecimal sumaKg,
                               boolean completa, List<FilaTrazabilidad> origen) {
    }

    @PersistenceContext
    private EntityManager em;

    private final LoteRepositorio lotes;
    private final LoteFincaRepositorio aportes;
    private final ProductoRepositorio productos;
    private final FincaRepositorio fincas;
    private final JdbcClient jdbc;

    public LoteServicio(LoteRepositorio lotes, LoteFincaRepositorio aportes, ProductoRepositorio productos,
                        FincaRepositorio fincas, JdbcClient jdbc) {
        this.lotes = lotes;
        this.aportes = aportes;
        this.productos = productos;
        this.fincas = fincas;
        this.jdbc = jdbc;
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public List<LoteExportacion> buscar(String estado, Integer idProducto) {
        UsuarioActual u = UsuarioActual.actual();
        if (u.esCliente()) {
            return lotes.delCliente(u.idCliente());
        }
        return lotes.buscar(estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(), idProducto);
    }

    /** Devuelve el lote si existe y el usuario puede verlo (un cliente solo ve lo que compro). */
    @Transactional(readOnly = true)
    public LoteExportacion obtener(Integer id) {
        LoteExportacion l = lotes.findById(id).orElseThrow(() -> ApiException.noEncontrado("El lote"));
        UsuarioActual u = UsuarioActual.actualONulo();
        if (u != null && u.esCliente() && (u.idCliente() == null || lotes.compradoPor(id, u.idCliente()) == 0)) {
            // 404 y no 403: no se confirma la existencia de lotes ajenos.
            throw ApiException.noEncontrado("El lote");
        }
        return l;
    }

    // ---------- Registro ----------

    public LoteExportacion crear(LoteSolicitud s) {
        Producto p = productos.findById(s.idProducto())
                .orElseThrow(() -> ApiException.invalido("El producto no existe."));
        validarPesos(s);
        String codigo = s.codigoLote() == null || s.codigoLote().isBlank() ? siguienteCodigo(p) : s.codigoLote();
        if (lotes.existsByCodigoLote(codigo)) {
            throw ApiException.conflicto("Ya existe un lote con el codigo " + codigo + ".");
        }
        LoteExportacion l = new LoteExportacion();
        l.setCodigoLote(codigo);
        l.setIdProducto(p.getIdProducto());
        l.setIdUsuarioRegistra(UsuarioActual.actual().idUsuario());
        l.setPesoNetoKg(s.pesoNetoKg());
        l.setPesoBrutoKg(s.pesoBrutoKg());
        l.setFechaCosecha(s.fechaCosecha());
        lotes.saveAndFlush(l);
        em.refresh(l); // trae estado_lote y fecha_registro que pone la base
        return l;
    }

    public LoteExportacion editar(Integer id, LoteSolicitud s) {
        LoteExportacion l = obtener(id);
        if (!LoteExportacion.REGISTRADO.equals(l.getEstadoLote())) {
            throw ApiException.invalido("Solo se puede editar un lote en estado REGISTRADO.");
        }
        validarPesos(s);
        boolean cambiaPeso = l.getPesoNetoKg().compareTo(s.pesoNetoKg()) != 0;
        if (cambiaPeso && aportes.existsByIdLote(id)) {
            throw ApiException.invalido(
                    "El lote ya tiene fincas asociadas: retirelas antes de cambiar el peso neto.");
        }
        if (s.codigoLote() != null && !s.codigoLote().isBlank() && !s.codigoLote().equals(l.getCodigoLote())) {
            if (lotes.existsByCodigoLote(s.codigoLote())) {
                throw ApiException.conflicto("Ya existe un lote con el codigo " + s.codigoLote() + ".");
            }
            l.setCodigoLote(s.codigoLote());
        }
        if (!productos.existsById(s.idProducto())) {
            throw ApiException.invalido("El producto no existe.");
        }
        l.setIdProducto(s.idProducto());
        l.setPesoNetoKg(s.pesoNetoKg());
        l.setPesoBrutoKg(s.pesoBrutoKg());
        l.setFechaCosecha(s.fechaCosecha());
        return lotes.saveAndFlush(l);
    }

    // ---------- Fincas de origen (SCRUM-16) ----------

    @Transactional(readOnly = true)
    public List<LoteFinca> aportes(Integer idLote) {
        obtener(idLote);
        return aportes.findByIdLoteOrderByPorcentajeAporteDesc(idLote);
    }

    public LoteFinca agregarAporte(Integer idLote, AporteSolicitud s) {
        LoteExportacion l = loteConOrigenEditable(idLote);
        if (!fincas.existsById(s.idFinca())) {
            throw ApiException.invalido("La finca no existe.");
        }
        // trg_lotfin_bi revalida duplicado, suma <= 100% y kg coherentes (tolerancia 0,5%).
        LoteFinca a = new LoteFinca();
        a.setIdLote(idLote);
        a.setIdFinca(s.idFinca());
        a.setPorcentajeAporte(s.porcentajeAporte());
        a.setKgAportados(kgSegun(l, s.porcentajeAporte(), s.kgAportados()));
        em.persist(a);
        em.flush();
        return a;
    }

    public LoteFinca editarAporte(Integer idLote, Integer idFinca, AporteEditar s) {
        LoteExportacion l = loteConOrigenEditable(idLote);
        LoteFinca a = aportes.findById(new LoteFinca.Clave(idLote, idFinca))
                .orElseThrow(() -> ApiException.noEncontrado("La finca en este lote"));
        a.setPorcentajeAporte(s.porcentajeAporte());
        a.setKgAportados(kgSegun(l, s.porcentajeAporte(), s.kgAportados()));
        return aportes.saveAndFlush(a);
    }

    public void quitarAporte(Integer idLote, Integer idFinca) {
        loteConOrigenEditable(idLote);
        LoteFinca a = aportes.findById(new LoteFinca.Clave(idLote, idFinca))
                .orElseThrow(() -> ApiException.noEncontrado("La finca en este lote"));
        aportes.delete(a);
        aportes.flush();
    }

    /** sp_cerrar_trazabilidad: comprueba suma = 100% y kg = peso neto. Falla con 422 si no. */
    public Map<String, Object> cerrarTrazabilidad(Integer idLote) {
        obtener(idLote);
        return jdbc.sql("CALL sp_cerrar_trazabilidad(?)").param(idLote).query().singleRow();
    }

    // ---------- Trazabilidad (SCRUM-19) ----------

    @Transactional(readOnly = true)
    public Trazabilidad trazabilidad(Integer idLote) {
        LoteExportacion l = obtener(idLote);
        Producto p = productos.findById(l.getIdProducto()).orElseThrow();
        List<FilaTrazabilidad> filas = jdbc.sql("""
                        select productor, nombre_finca, area_hectareas, latitud, longitud,
                               porcentaje_aporte, kg_aportados, municipio, departamento
                          from v_trazabilidad_lote
                         where codigo_lote = ?
                         order by porcentaje_aporte desc""")
                .param(l.getCodigoLote())
                .query((rs, n) -> new FilaTrazabilidad(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                        rs.getString(8), rs.getString(9)))
                .list();
        BigDecimal pct = filas.stream().map(FilaTrazabilidad::porcentajeAporte).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal kg = filas.stream().map(FilaTrazabilidad::kgAportados).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tolerancia = l.getPesoNetoKg().multiply(new BigDecimal("0.005"));
        boolean completa = !filas.isEmpty()
                && pct.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) <= 0
                && kg.subtract(l.getPesoNetoKg()).abs().compareTo(tolerancia) <= 0;
        return new Trazabilidad(l.getCodigoLote(), p.getNombreComercial(), p.getCodigoHs(), l.getPesoNetoKg(),
                l.getEstadoLote(), pct, kg, completa, filas);
    }

    // ---------- Apoyo ----------

    private LoteExportacion loteConOrigenEditable(Integer idLote) {
        LoteExportacion l = obtener(idLote);
        if (!ORIGEN_EDITABLE.contains(l.getEstadoLote())) {
            throw ApiException.invalido("El origen de un lote en estado " + l.getEstadoLote()
                    + " ya no se puede modificar.");
        }
        return l;
    }

    private static BigDecimal kgSegun(LoteExportacion l, BigDecimal porcentaje, BigDecimal kgIndicados) {
        if (kgIndicados != null) {
            return kgIndicados;
        }
        return l.getPesoNetoKg().multiply(porcentaje).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static void validarPesos(LoteSolicitud s) {
        if (s.pesoBrutoKg().compareTo(s.pesoNetoKg()) < 0) {
            throw ApiException.invalido("El peso bruto no puede ser menor que el peso neto.");
        }
    }

    /** CAF-2026-0001 / CAC-2026-0001 segun el tipo de producto. */
    private String siguienteCodigo(Producto p) {
        String prefijo = ("CAFE".equals(p.getTipo()) ? "CAF-" : "CAC-") + LocalDate.now().getYear() + "-";
        return prefijo + String.format("%04d", lotes.ultimoConsecutivo(prefijo) + 1);
    }
}
