package com.ritech.exports.documentos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Documentos de exportacion", description = "SCRUM-27 factura y lista de empaque; SCRUM-24 DDS EUDR")
public class DocumentoControlador {

    private final DocumentoServicio servicio;

    public DocumentoControlador(DocumentoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/ordenes/{id}/factura")
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','ROLE_CLIENTE_UE')")
    @Operation(summary = "Factura comercial en PDF de una orden CONFIRMADA, EN_TRANSITO o ENTREGADA",
            description = "La primera vez registra el documento FC-AAAA-NNNN en documentos_exportacion. "
                    + "Con descargar=true el navegador lo baja en vez de abrirlo.")
    public ResponseEntity<byte[]> factura(@PathVariable Integer id,
                                          @RequestParam(defaultValue = "false") boolean descargar) {
        return responder(servicio.factura(id), descargar);
    }

    @GetMapping("/api/ordenes/{id}/lista-empaque")
    @PreAuthorize("hasAnyAuthority('ORDENES_GESTIONAR','ROLE_CLIENTE_UE')")
    @Operation(summary = "Lista de empaque en PDF de una orden CONFIRMADA, EN_TRANSITO o ENTREGADA")
    public ResponseEntity<byte[]> listaEmpaque(@PathVariable Integer id,
                                               @RequestParam(defaultValue = "false") boolean descargar) {
        return responder(servicio.listaEmpaque(id), descargar);
    }

    @GetMapping("/api/lotes/{id}/dds")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    @Operation(summary = "Declaracion de diligencia debida EUDR del lote en PDF",
            description = "Exige trazabilidad completa (100 %) y dictamen CUMPLE. Un CLIENTE_UE solo la obtiene "
                    + "de los lotes de sus ordenes.")
    public ResponseEntity<byte[]> dds(@PathVariable Integer id,
                                      @RequestParam(defaultValue = "false") boolean descargar) {
        return responder(servicio.ddsPdf(id), descargar);
    }

    @GetMapping("/api/lotes/{id}/dds/geojson")
    @PreAuthorize("hasAuthority('LOTES_CONSULTAR')")
    @Operation(summary = "Geolocalizacion de las fincas del lote en GeoJSON, para cargar en TRACES")
    public ResponseEntity<byte[]> ddsGeojson(@PathVariable Integer id) {
        return responder(servicio.ddsGeojson(id), true);
    }

    static ResponseEntity<byte[]> responder(DocumentoServicio.Archivo a, boolean descargar) {
        ContentDisposition cd = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(a.nombre()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(a.tipoContenido()))
                .body(a.contenido());
    }
}
