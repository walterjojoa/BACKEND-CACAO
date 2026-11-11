package com.ritech.exports.documentos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritech.exports.comun.ApiException;
import com.ritech.exports.lotes.LoteExportacion;
import com.ritech.exports.lotes.LoteServicio;
import com.ritech.exports.maestros.Producto;
import com.ritech.exports.maestros.ProductoRepositorio;
import com.ritech.exports.ordenes.OrdenExportacion;
import com.ritech.exports.ordenes.OrdenServicio;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-27 factura comercial y lista de empaque; SCRUM-24 DDS EUDR. Los PDF se generan al vuelo con los
 * datos actuales; la factura y la lista quedan registradas en documentos_exportacion con su numero.
 */
@Service
@Transactional
public class DocumentoServicio {

    public static final String PDF = "application/pdf";
    public static final String GEOJSON = "application/geo+json";

    /** Solo una orden confirmada tiene precios y lotes definitivos. */
    private static final Set<String> ORDEN_EMITIBLE = Set.of(OrdenExportacion.CONFIRMADA,
            OrdenExportacion.EN_TRANSITO, OrdenExportacion.ENTREGADA);

    public record Archivo(String nombre, String tipoContenido, byte[] contenido) {
    }

    private final OrdenServicio ordenes;
    private final LoteServicio lotes;
    private final ProductoRepositorio productos;
    private final JdbcClient jdbc;
    private final DatosEmpresa empresa;
    private final ObjectMapper json;

    public DocumentoServicio(OrdenServicio ordenes, LoteServicio lotes, ProductoRepositorio productos,
                             JdbcClient jdbc, DatosEmpresa empresa, ObjectMapper json) {
        this.ordenes = ordenes;
        this.lotes = lotes;
        this.productos = productos;
        this.jdbc = jdbc;
        this.empresa = empresa;
        this.json = json;
    }

    // ---------- SCRUM-27 ----------

    public Archivo factura(Integer idOrden) {
        DocumentosOrden.Datos d = datosOrden(idOrden);
        Emitido doc = emitir(d.orden(), "FACTURA_COMERCIAL", "FC-", "factura");
        return new Archivo(doc.numero() + ".pdf", PDF,
                DocumentosOrden.factura(empresa, d, doc.numero(), doc.fecha()));
    }

    public Archivo listaEmpaque(Integer idOrden) {
        DocumentosOrden.Datos d = datosOrden(idOrden);
        Emitido doc = emitir(d.orden(), "LISTA_EMPAQUE", "LE-", "lista-empaque");
        return new Archivo(doc.numero() + ".pdf", PDF,
                DocumentosOrden.listaEmpaque(empresa, d, doc.numero(), doc.fecha()));
    }

    // ---------- SCRUM-24 ----------

    public Archivo ddsPdf(Integer idLote) {
        DeclaracionDds.Datos d = datosDds(idLote);
        return new Archivo(d.referencia() + ".pdf", PDF, DeclaracionDds.pdf(empresa, d, LocalDate.now()));
    }

    public Archivo ddsGeojson(Integer idLote) {
        DeclaracionDds.Datos d = datosDds(idLote);
        try {
            return new Archivo(d.referencia() + ".geojson", GEOJSON,
                    json.writerWithDefaultPrettyPrinter().writeValueAsBytes(DeclaracionDds.geojson(json, d)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Datos ----------

    DocumentosOrden.Datos datosOrden(Integer idOrden) {
        OrdenExportacion o = ordenes.obtener(idOrden); // 404 si un cliente pide una orden ajena
        if (!ORDEN_EMITIBLE.contains(o.getEstadoOrden())) {
            throw ApiException.invalido("Los documentos se emiten para ordenes confirmadas; la orden "
                    + o.getNumeroOrden() + " esta " + o.getEstadoOrden() + ".");
        }
        DocumentosOrden.Cliente cliente = jdbc.sql("""
                        select c.razon_social, c.numero_eori, c.nit_vat, c.direccion, c.ciudad, p.nombre,
                               c.email_contacto
                          from clientes_ue c join paises p on p.id_pais = c.id_pais
                         where c.id_cliente = ?""")
                .param(o.getIdCliente())
                .query((rs, n) -> new DocumentosOrden.Cliente(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)))
                .single();
        List<DocumentosOrden.Linea> lineas = jdbc.sql("""
                        select l.codigo_lote, p.tipo, p.nombre_comercial, p.variedad, p.codigo_hs, l.fecha_cosecha,
                               ol.kg_asignados, ol.precio_unitario, l.peso_neto_kg, l.peso_bruto_kg
                          from ordenes_lotes ol
                          join lotes_exportacion l on l.id_lote = ol.id_lote
                          join productos p on p.id_producto = l.id_producto
                         where ol.id_orden = ?
                         order by l.codigo_lote""")
                .param(idOrden)
                .query((rs, n) -> new DocumentosOrden.Linea(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), fecha(rs.getDate(6)), rs.getBigDecimal(7),
                        rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10)))
                .list();
        DocumentosOrden.Embarque embarque = jdbc.sql("""
                        select t.nombre, t.tipo, e.numero_contenedor, e.booking_bl, e.puerto_origen,
                               e.puerto_destino, e.fecha_estimada_zarpe, e.fecha_estimada_arribo, e.estado_envio
                          from envios e join transportistas t on t.id_transportista = e.id_transportista
                         where e.id_orden = ?
                         order by e.id_envio desc
                         limit 1""")
                .param(idOrden)
                .query((rs, n) -> new DocumentosOrden.Embarque(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), fecha(rs.getDate(7)),
                        fecha(rs.getDate(8)), rs.getString(9)))
                .optional().orElse(null);
        return new DocumentosOrden.Datos(o, cliente, lineas, embarque);
    }

    DeclaracionDds.Datos datosDds(Integer idLote) {
        LoteExportacion l = lotes.obtener(idLote); // 404 si un cliente pide un lote que no compro
        LoteServicio.Trazabilidad traza = lotes.trazabilidad(idLote);
        if (traza.origen().isEmpty()) {
            throw ApiException.invalido("El lote " + l.getCodigoLote() + " no tiene fincas de origen; registre la "
                    + "trazabilidad antes de emitir la DDS.");
        }
        if (!traza.completa()) {
            throw ApiException.invalido("La trazabilidad del lote " + l.getCodigoLote() + " esta incompleta ("
                    + traza.sumaPorcentaje().stripTrailingZeros().toPlainString() + " % del peso neto). La DDS "
                    + "exige declarar todas las parcelas de origen.");
        }
        String dictamen = jdbc.sql("select dictamen from v_dictamen_lote where id_lote = ?")
                .param(idLote).query(String.class).optional().orElse("SIN_VALIDAR");
        if (!"CUMPLE".equals(dictamen)) {
            throw ApiException.invalido("El lote " + l.getCodigoLote() + " tiene dictamen " + dictamen + "; ejecute "
                    + "la validacion normativa (POST /api/lotes/" + idLote + "/validar) y corrija lo pendiente "
                    + "antes de declarar riesgo insignificante.");
        }
        Producto p = productos.findById(l.getIdProducto()).orElseThrow();
        List<DeclaracionDds.Finca> fincas = jdbc.sql("""
                        select f.nombre_finca, pr.nombre, pr.nit_o_cedula, pr.municipio, pr.departamento,
                               f.area_hectareas, f.latitud, f.longitud, f.poligono_geojson,
                               f.fecha_verificacion_deforestacion, lf.porcentaje_aporte, lf.kg_aportados
                          from lotes_fincas lf
                          join fincas f on f.id_finca = lf.id_finca
                          join productores pr on pr.id_productor = f.id_productor
                         where lf.id_lote = ?
                         order by lf.porcentaje_aporte desc, f.nombre_finca""")
                .param(idLote)
                .query((rs, n) -> new DeclaracionDds.Finca(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                        rs.getBigDecimal(8), rs.getString(9), fecha(rs.getDate(10)), rs.getBigDecimal(11),
                        rs.getBigDecimal(12)))
                .list();
        DeclaracionDds.Importador importador = jdbc.sql("""
                        select o.numero_orden, c.razon_social, c.numero_eori, pa.nombre
                          from ordenes_lotes ol
                          join ordenes_exportacion o on o.id_orden = ol.id_orden
                          join clientes_ue c on c.id_cliente = o.id_cliente
                          join paises pa on pa.id_pais = c.id_pais
                         where ol.id_lote = ? and o.estado_orden <> 'ANULADA'
                         order by o.id_orden desc
                         limit 1""")
                .param(idLote)
                .query((rs, n) -> new DeclaracionDds.Importador(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4)))
                .optional().orElse(null);
        return new DeclaracionDds.Datos("DDS-" + l.getCodigoLote(), l.getCodigoLote(), p.getTipo(),
                p.getNombreComercial(), p.getVariedad(), p.getCodigoHs(), l.getPesoNetoKg(), l.getFechaCosecha(),
                l.getEstadoLote(), dictamen, importador, fincas);
    }

    // ---------- Registro en documentos_exportacion ----------

    private record Emitido(String numero, LocalDate fecha) {
    }

    /**
     * Devuelve el documento ya registrado de la orden o lo registra la primera vez. El numero sigue al de la
     * orden: ORD-2026-0001 -> FC-2026-0001 / LE-2026-0001 (igual que los datos semilla).
     */
    private Emitido emitir(OrdenExportacion o, String tipo, String prefijo, String ruta) {
        return registrado(o.getIdOrden(), tipo).orElseGet(() -> {
            String numero = prefijo + o.getNumeroOrden().replaceFirst("^ORD-", "");
            jdbc.sql("""
                            insert into documentos_exportacion (id_orden, tipo_documento, numero, url_archivo)
                            values (?, ?, ?, ?)""")
                    .params(o.getIdOrden(), tipo, numero, "/api/ordenes/" + o.getIdOrden() + "/" + ruta)
                    .update();
            return registrado(o.getIdOrden(), tipo).orElse(new Emitido(numero, LocalDate.now()));
        });
    }

    private java.util.Optional<Emitido> registrado(Integer idOrden, String tipo) {
        return jdbc.sql("select numero, fecha_emision from documentos_exportacion where id_orden = ? and tipo_documento = ?")
                .params(idOrden, tipo)
                .query((rs, n) -> new Emitido(rs.getString(1), fecha(rs.getDate(2))))
                .optional();
    }

    private static LocalDate fecha(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
