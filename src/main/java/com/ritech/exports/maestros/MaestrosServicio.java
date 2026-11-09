package com.ritech.exports.maestros;

import com.fasterxml.jackson.databind.JsonNode;
import com.ritech.exports.comun.ApiException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Productos (SCRUM-13), productores y fincas (SCRUM-14). */
@Service
@Transactional
public class MaestrosServicio {

    private static final BigDecimal LIMITE_HECTAREAS_SIN_POLIGONO = new BigDecimal("4");

    public record ProductoSolicitud(
            @NotBlank @Pattern(regexp = "CAFE|CACAO") String tipo,
            @NotBlank @Size(max = 100) String nombreComercial,
            @Size(max = 80) String variedad,
            @NotBlank @Pattern(regexp = "^[0-9]{4}([0-9]{2}){0,3}$", message = "codigo HS de 4 a 10 digitos")
            String codigoHs,
            @Size(max = 10) String unidadMedida) {
    }

    public record ProductorSolicitud(
            @NotBlank @Size(max = 20) String nitOCedula,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 80) String municipio,
            @NotBlank @Size(max = 80) String departamento,
            @Size(max = 25) String telefono) {
    }

    public record FincaSolicitud(
            @NotBlank @Size(max = 120) String nombreFinca,
            @NotNull @Positive BigDecimal areaHectareas,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
            JsonNode poligonoGeojson,
            @PastOrPresent LocalDate fechaVerificacionDeforestacion) {
    }

    private final ProductoRepositorio productos;
    private final ProductorRepositorio productores;
    private final FincaRepositorio fincas;

    public MaestrosServicio(ProductoRepositorio productos, ProductorRepositorio productores, FincaRepositorio fincas) {
        this.productos = productos;
        this.productores = productores;
        this.fincas = fincas;
    }

    // ---------- Productos ----------

    @Transactional(readOnly = true)
    public List<Producto> productos() {
        return productos.findAllByOrderByTipoAscNombreComercialAsc();
    }

    @Transactional(readOnly = true)
    public Producto producto(Integer id) {
        return productos.findById(id).orElseThrow(() -> ApiException.noEncontrado("El producto"));
    }

    public Producto guardarProducto(Integer id, ProductoSolicitud s) {
        Producto p = id == null ? new Producto() : producto(id);
        p.setTipo(s.tipo());
        p.setNombreComercial(s.nombreComercial().trim());
        p.setVariedad(blancoANulo(s.variedad()));
        p.setCodigoHs(s.codigoHs());
        p.setUnidadMedida(s.unidadMedida() == null || s.unidadMedida().isBlank() ? "KG" : s.unidadMedida().trim());
        return productos.saveAndFlush(p);
    }

    /** Falla con 409 si ya hay lotes o reglas que usan el producto (FK RESTRICT). */
    public void eliminarProducto(Integer id) {
        productos.delete(producto(id));
        productos.flush();
    }

    // ---------- Productores ----------

    @Transactional(readOnly = true)
    public List<Productor> productores() {
        return productores.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Productor productor(Integer id) {
        return productores.findById(id).orElseThrow(() -> ApiException.noEncontrado("El productor"));
    }

    public Productor guardarProductor(Integer id, ProductorSolicitud s) {
        Productor p = id == null ? new Productor() : productor(id);
        String nit = s.nitOCedula().trim();
        if (!nit.equals(p.getNitOCedula()) && productores.existsByNitOCedula(nit)) {
            throw ApiException.conflicto("Ya existe un productor con el NIT o cedula " + nit + ".");
        }
        p.setNitOCedula(nit);
        p.setNombre(s.nombre().trim());
        p.setMunicipio(s.municipio().trim());
        p.setDepartamento(s.departamento().trim());
        p.setTelefono(blancoANulo(s.telefono()));
        return productores.saveAndFlush(p);
    }

    // ---------- Fincas ----------

    @Transactional(readOnly = true)
    public List<Finca> fincasDe(Integer idProductor) {
        productor(idProductor);
        return fincas.findByIdProductorOrderByNombreFincaAsc(idProductor);
    }

    @Transactional(readOnly = true)
    public Finca finca(Integer id) {
        return fincas.findById(id).orElseThrow(() -> ApiException.noEncontrado("La finca"));
    }

    public Finca crearFinca(Integer idProductor, FincaSolicitud s) {
        productor(idProductor);
        Finca f = new Finca();
        f.setIdProductor(idProductor);
        copiar(s, f);
        return fincas.saveAndFlush(f);
    }

    public Finca editarFinca(Integer id, FincaSolicitud s) {
        Finca f = finca(id);
        copiar(s, f);
        return fincas.saveAndFlush(f);
    }

    private static void copiar(FincaSolicitud s, Finca f) {
        String poligono = null;
        if (s.poligonoGeojson() != null && !s.poligonoGeojson().isNull()) {
            JsonNode g = s.poligonoGeojson();
            if (!g.hasNonNull("type") || !g.hasNonNull("coordinates")) {
                throw ApiException.invalido("El poligono debe ser GeoJSON con 'type' y 'coordinates'.");
            }
            poligono = g.toString();
        }
        // EUDR (Reglamento 2023/1115): mas de 4 ha exige poligono. La base lo vuelve a comprobar.
        if (s.areaHectareas().compareTo(LIMITE_HECTAREAS_SIN_POLIGONO) > 0 && poligono == null) {
            throw ApiException.invalido("Las fincas de mas de 4 hectareas requieren poligono de geolocalizacion (EUDR).");
        }
        f.setNombreFinca(s.nombreFinca().trim());
        f.setAreaHectareas(s.areaHectareas());
        f.setLatitud(s.latitud());
        f.setLongitud(s.longitud());
        f.setPoligonoGeojson(poligono);
        f.setFechaVerificacionDeforestacion(s.fechaVerificacionDeforestacion());
    }

    private static String blancoANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
