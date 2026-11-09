package com.ritech.exports.cumplimiento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Cumplimiento UE", description = "SCRUM-20/21/22/23: certificados, reglas, validacion y alertas")
public class CumplimientoControlador {

    private final CertificadoServicio certificados;
    private final ReglaServicio reglas;
    private final ValidacionServicio validacion;

    public CumplimientoControlador(CertificadoServicio certificados, ReglaServicio reglas,
                                   ValidacionServicio validacion) {
        this.certificados = certificados;
        this.reglas = reglas;
        this.validacion = validacion;
    }

    // ----- Certificados

    @GetMapping("/certificaciones")
    @Operation(summary = "Catalogo de tipos de certificado")
    public List<Certificacion> catalogo() {
        return certificados.catalogo();
    }

    @GetMapping("/lotes/{id}/certificados")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    public List<LoteCertificacion> deLote(@PathVariable Integer id) {
        return certificados.deLote(id);
    }

    @PostMapping("/lotes/{id}/certificados")
    @PreAuthorize("hasAuthority('CERTIFICADOS_CARGAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Carga o renueva un certificado del lote con su vigencia")
    public LoteCertificacion cargar(@PathVariable Integer id,
                                    @Valid @RequestBody CertificadoServicio.CertificadoSolicitud s) {
        return certificados.cargar(id, s);
    }

    @PostMapping(path = "/lotes/{id}/certificados/{idCertificacion}/soporte",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CERTIFICADOS_CARGAR')")
    @Operation(summary = "Adjunta el soporte digitalizado (PDF, JPG o PNG, max. 10 MB)")
    public LoteCertificacion subirSoporte(@PathVariable Integer id, @PathVariable Integer idCertificacion,
                                          @RequestPart("archivo") MultipartFile archivo) {
        return certificados.subirSoporte(id, idCertificacion, archivo);
    }

    @GetMapping("/lotes/{id}/certificados/{idCertificacion}/soporte")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    public ResponseEntity<Resource> descargarSoporte(@PathVariable Integer id, @PathVariable Integer idCertificacion) {
        CertificadoServicio.Soporte s = certificados.soporte(id, idCertificacion);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(s.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(s.nombre()).build().toString())
                .body(s.archivo());
    }

    @GetMapping("/certificados/por-vencer")
    @PreAuthorize("hasAnyAuthority('CERTIFICADOS_CARGAR','ORDENES_GESTIONAR')")
    @Operation(summary = "SCRUM-23: certificados vigentes que vencen en los proximos N dias (30 por defecto)")
    public List<CertificadoServicio.Alerta> porVencer(@RequestParam(required = false) Integer dias) {
        return certificados.porVencer(dias);
    }

    // ----- Reglas

    @GetMapping("/reglas")
    @PreAuthorize("hasAnyAuthority('REGLAS_ADMINISTRAR','VALIDACION_EJECUTAR')")
    public List<ReglaNormativa> reglas() {
        return reglas.listar();
    }

    @GetMapping("/reglas/parametros")
    @PreAuthorize("hasAuthority('REGLAS_ADMINISTRAR')")
    @Operation(summary = "Parametros que el motor de validacion sabe evaluar")
    public Map<String, String> parametros() {
        return ReglaServicio.PARAMETROS_LOTE;
    }

    @GetMapping("/reglas/{id}")
    @PreAuthorize("hasAnyAuthority('REGLAS_ADMINISTRAR','VALIDACION_EJECUTAR')")
    public ReglaNormativa regla(@PathVariable Integer id) {
        return reglas.obtener(id);
    }

    @PostMapping("/reglas")
    @PreAuthorize("hasAuthority('REGLAS_ADMINISTRAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReglaNormativa crearRegla(@Valid @RequestBody ReglaServicio.ReglaSolicitud s) {
        return reglas.crear(s);
    }

    @PutMapping("/reglas/{id}")
    @PreAuthorize("hasAuthority('REGLAS_ADMINISTRAR')")
    public ReglaNormativa editarRegla(@PathVariable Integer id, @Valid @RequestBody ReglaServicio.ReglaSolicitud s) {
        return reglas.editar(id, s);
    }

    @PatchMapping("/reglas/{id}/activa")
    @PreAuthorize("hasAuthority('REGLAS_ADMINISTRAR')")
    public ReglaNormativa activar(@PathVariable Integer id, @RequestParam boolean valor) {
        return reglas.activar(id, valor);
    }

    // ----- Validacion

    @PostMapping("/lotes/{id}/validar")
    @PreAuthorize("hasAuthority('VALIDACION_EJECUTAR')")
    @Operation(summary = "Ejecuta sp_validar_lote y devuelve el dictamen CUMPLE / NO_CUMPLE")
    public ValidacionServicio.Dictamen validar(@PathVariable Integer id) {
        return validacion.validar(id);
    }

    @GetMapping("/lotes/{id}/dictamen")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    @Operation(summary = "Dictamen de la ultima validacion, regla por regla")
    public ValidacionServicio.Dictamen dictamen(@PathVariable Integer id) {
        return validacion.dictamen(id);
    }
}
