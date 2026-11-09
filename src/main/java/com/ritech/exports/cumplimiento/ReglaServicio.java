package com.ritech.exports.cumplimiento;

import com.ritech.exports.comun.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-21: administrar el catalogo de reglas normativas UE. */
@Service
@Transactional
public class ReglaServicio {

    /**
     * Parametros que sp_validar_lote sabe medir. Una regla con otro parametro
     * siempre daria NO_CUMPLE ("SIN DATO") y bloquearia todos los lotes.
     */
    public static final Map<String, String> PARAMETROS_LOTE = Map.of(
            "geolocalizacion_fincas", "% de fincas de origen con latitud y longitud",
            "poligono_si_area_mayor_4ha", "1 si toda finca > 4 ha tiene poligono",
            "suma_aportes_finca", "suma de porcentajes de aporte",
            "ocratoxina_a_cafe", "ocratoxina A de la ultima inspeccion (ug/kg)",
            "aflatoxinas_cacao", "aflatoxinas de la ultima inspeccion (ug/kg)",
            "humedad_cafe_verde", "humedad de la ultima inspeccion (%)",
            "certificado_fitosanitario_vigente", "1 si hay fitosanitario vigente");

    /** Las de ambito ORDEN estan escritas dentro de trg_ordlot_bi: su limite no es configurable. */
    public static final Set<String> PARAMETROS_ORDEN = Set.of("eori_importador_valido");

    public record ReglaSolicitud(
            @NotBlank @Pattern(regexp = "^R-UE-[0-9]{3}$", message = "formato R-UE-000") String codigoRegla,
            @NotBlank @Size(max = 120) String normativaReferencia,
            Integer idProducto,
            @Pattern(regexp = "CAFE|CACAO|AMBOS") String aplicaTipo,
            @NotBlank String parametro,
            @NotBlank @Pattern(regexp = "=|<>|<|<=|>|>=") String operador,
            @NotBlank @Size(max = 40) String valorLimite,
            @NotBlank @Size(max = 300) String mensajeError,
            Boolean activa) {
    }

    private final ReglaRepositorio reglas;

    public ReglaServicio(ReglaRepositorio reglas) {
        this.reglas = reglas;
    }

    @Transactional(readOnly = true)
    public List<ReglaNormativa> listar() {
        return reglas.findAllByOrderByCodigoReglaAsc();
    }

    @Transactional(readOnly = true)
    public ReglaNormativa obtener(Integer id) {
        return reglas.findById(id).orElseThrow(() -> ApiException.noEncontrado("La regla"));
    }

    public ReglaNormativa crear(ReglaSolicitud s) {
        if (reglas.existsByCodigoRegla(s.codigoRegla())) {
            throw ApiException.conflicto("Ya existe la regla " + s.codigoRegla() + ".");
        }
        ReglaNormativa r = new ReglaNormativa();
        r.setCodigoRegla(s.codigoRegla());
        copiar(s, r);
        return reglas.saveAndFlush(r);
    }

    public ReglaNormativa editar(Integer id, ReglaSolicitud s) {
        ReglaNormativa r = obtener(id);
        if (!r.getCodigoRegla().equals(s.codigoRegla())) {
            throw ApiException.invalido("El codigo de una regla no se cambia; cree una nueva.");
        }
        if ("ORDEN".equals(r.getAmbito()) && (!r.getValorLimite().equals(s.valorLimite())
                || !r.getOperador().equals(s.operador()) || !r.getParametro().equals(s.parametro()))) {
            throw ApiException.invalido(
                    "Las reglas de ambito ORDEN las aplica la base al asignar lotes; solo se edita su texto y si esta activa.");
        }
        copiar(s, r);
        return reglas.saveAndFlush(r);
    }

    public ReglaNormativa activar(Integer id, boolean activa) {
        ReglaNormativa r = obtener(id);
        r.setActiva(activa);
        return reglas.saveAndFlush(r);
    }

    private static void copiar(ReglaSolicitud s, ReglaNormativa r) {
        String ambito;
        if (PARAMETROS_LOTE.containsKey(s.parametro())) {
            ambito = "LOTE";
        } else if (PARAMETROS_ORDEN.contains(s.parametro())) {
            ambito = "ORDEN";
        } else {
            throw ApiException.invalido("Parametro desconocido. Validos: " + PARAMETROS_LOTE.keySet()
                    + " y " + PARAMETROS_ORDEN + ".");
        }
        String limite = s.valorLimite().trim();
        if (!limite.equalsIgnoreCase("true") && !limite.equalsIgnoreCase("si")) {
            try {
                new BigDecimal(limite);
            } catch (NumberFormatException e) {
                throw ApiException.invalido("El valor limite debe ser un numero o 'true'.");
            }
        }
        r.setNormativaReferencia(s.normativaReferencia().trim());
        r.setIdProducto(s.idProducto());
        r.setAplicaTipo(s.aplicaTipo() == null ? "AMBOS" : s.aplicaTipo());
        r.setAmbito(ambito);
        r.setParametro(s.parametro());
        r.setOperador(s.operador());
        r.setValorLimite(limite);
        r.setMensajeError(s.mensajeError().trim());
        if (s.activa() != null) {
            r.setActiva(s.activa());
        } else if (r.getActiva() == null) {
            r.setActiva(true);
        }
    }
}
