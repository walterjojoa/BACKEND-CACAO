package com.ritech.exports.lotes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotes")
@Tag(name = "Lotes", description = "SCRUM-15/16/17/18/19: lotes, origen, inspecciones y trazabilidad")
public class LoteControlador {

    private final LoteServicio lotes;
    private final InspeccionServicio inspecciones;

    public LoteControlador(LoteServicio lotes, InspeccionServicio inspecciones) {
        this.lotes = lotes;
        this.inspecciones = inspecciones;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    @Operation(summary = "Lista lotes; un CLIENTE_UE solo ve los lotes de sus ordenes")
    public List<LoteExportacion> buscar(@RequestParam(required = false) String estado,
                                        @RequestParam(required = false) Integer idProducto) {
        return lotes.buscar(estado, idProducto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    public LoteExportacion obtener(@PathVariable Integer id) {
        return lotes.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LOTES_CREAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un lote; si no se envia codigoLote se genera CAF-AAAA-NNNN / CAC-AAAA-NNNN")
    public LoteExportacion crear(@Valid @RequestBody LoteServicio.LoteSolicitud s) {
        return lotes.crear(s);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LOTES_CREAR')")
    public LoteExportacion editar(@PathVariable Integer id, @Valid @RequestBody LoteServicio.LoteSolicitud s) {
        return lotes.editar(id, s);
    }

    // ----- Origen

    @GetMapping("/{id}/fincas")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    public List<LoteFinca> aportes(@PathVariable Integer id) {
        return lotes.aportes(id);
    }

    @PostMapping("/{id}/fincas")
    @PreAuthorize("hasAuthority('LOTES_TRAZABILIDAD')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Asocia una finca de origen con su porcentaje (kg se calcula si no se envia)")
    public LoteFinca agregarAporte(@PathVariable Integer id, @Valid @RequestBody LoteServicio.AporteSolicitud s) {
        return lotes.agregarAporte(id, s);
    }

    @PutMapping("/{id}/fincas/{idFinca}")
    @PreAuthorize("hasAuthority('LOTES_TRAZABILIDAD')")
    public LoteFinca editarAporte(@PathVariable Integer id, @PathVariable Integer idFinca,
                                  @Valid @RequestBody LoteServicio.AporteEditar s) {
        return lotes.editarAporte(id, idFinca, s);
    }

    @DeleteMapping("/{id}/fincas/{idFinca}")
    @PreAuthorize("hasAuthority('LOTES_TRAZABILIDAD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarAporte(@PathVariable Integer id, @PathVariable Integer idFinca) {
        lotes.quitarAporte(id, idFinca);
    }

    @PostMapping("/{id}/cerrar-trazabilidad")
    @PreAuthorize("hasAuthority('LOTES_TRAZABILIDAD')")
    @Operation(summary = "Verifica que el origen sume 100% y coincida con el peso neto")
    public Map<String, Object> cerrarTrazabilidad(@PathVariable Integer id) {
        return lotes.cerrarTrazabilidad(id);
    }

    @GetMapping("/{id}/trazabilidad")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    @Operation(summary = "Cadena completa: producto, productores, fincas, coordenadas y aportes")
    public LoteServicio.Trazabilidad trazabilidad(@PathVariable Integer id) {
        return lotes.trazabilidad(id);
    }

    // ----- Calidad

    @GetMapping("/{id}/inspecciones")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    public List<InspeccionCalidad> inspecciones(@PathVariable Integer id) {
        return inspecciones.deLote(id);
    }

    @PostMapping("/{id}/inspecciones")
    @PreAuthorize("hasAuthority('INSPECCION_REGISTRAR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra la inspeccion; APROBADO/RECHAZADO mueve el estado del lote")
    public InspeccionServicio.InspeccionRegistrada inspeccionar(@PathVariable Integer id,
                                                                @Valid @RequestBody InspeccionServicio.InspeccionSolicitud s) {
        return inspecciones.registrar(id, s);
    }
}
