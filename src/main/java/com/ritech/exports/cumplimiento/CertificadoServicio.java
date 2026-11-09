package com.ritech.exports.cumplimiento;

import com.ritech.exports.comun.ApiException;
import com.ritech.exports.lotes.LoteServicio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** SCRUM-20 cargar certificados con vigencia y soporte; SCRUM-23 alertas de vencimiento. */
@Service
@Transactional
public class CertificadoServicio {

    private static final Logger log = LoggerFactory.getLogger(CertificadoServicio.class);

    /** Tipos de archivo aceptados como soporte digitalizado. */
    private static final Map<String, String> TIPOS = Map.of(
            "application/pdf", ".pdf", "image/jpeg", ".jpg", "image/png", ".png");

    public record CertificadoSolicitud(
            @NotNull Integer idCertificacion,
            @NotBlank @Size(max = 60) String numeroDocumento,
            @NotNull LocalDate fechaEmision,
            @NotNull LocalDate fechaVencimiento) {
    }

    public record Alerta(Integer idLote, String codigoLote, String certificado, String numeroDocumento,
                         LocalDate fechaVencimiento, long diasRestantes) {
    }

    public record Soporte(Resource archivo, String tipo, String nombre) {
    }

    private final LoteCertificacionRepositorio certificadosLote;
    private final CertificacionRepositorio catalogo;
    private final LoteServicio lotes;
    private final Path directorio;
    private final int diasAlertaPorDefecto;

    public CertificadoServicio(LoteCertificacionRepositorio certificadosLote, CertificacionRepositorio catalogo,
                               LoteServicio lotes,
                               @Value("${ritech.almacenamiento.directorio}") String directorio,
                               @Value("${ritech.certificados.dias-alerta}") int diasAlerta) {
        this.certificadosLote = certificadosLote;
        this.catalogo = catalogo;
        this.lotes = lotes;
        this.directorio = Path.of(directorio).toAbsolutePath().normalize().resolve("certificados");
        this.diasAlertaPorDefecto = diasAlerta;
    }

    @Transactional(readOnly = true)
    public List<Certificacion> catalogo() {
        return catalogo.findAllByOrderByObligatoriaUeDescNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<LoteCertificacion> deLote(Integer idLote) {
        lotes.obtener(idLote);
        return certificadosLote.findByIdLoteOrderByFechaVencimientoAsc(idLote);
    }

    public LoteCertificacion cargar(Integer idLote, CertificadoSolicitud s) {
        lotes.obtener(idLote);
        if (!catalogo.existsById(s.idCertificacion())) {
            throw ApiException.invalido("El tipo de certificado no existe.");
        }
        // Los mismos controles de trg_lotcert_bi, con mensaje temprano (CP-028 / CP-029).
        if (!s.fechaVencimiento().isAfter(s.fechaEmision())) {
            throw ApiException.invalido("La fecha de vencimiento debe ser posterior a la fecha de emision.");
        }
        if (!s.fechaVencimiento().isAfter(LocalDate.now())) {
            throw ApiException.invalido("No es posible cargar un certificado ya vencido.");
        }
        if (s.fechaEmision().isAfter(LocalDate.now())) {
            throw ApiException.invalido("La fecha de emision no puede ser futura.");
        }
        LoteCertificacion.Clave clave = new LoteCertificacion.Clave(idLote, s.idCertificacion());
        LoteCertificacion c = certificadosLote.findById(clave).orElse(null);
        if (c == null) {
            c = new LoteCertificacion();
            c.setIdLote(idLote);
            c.setIdCertificacion(s.idCertificacion());
        }
        // Reemplazar un certificado del mismo tipo es la renovacion: vuelve a VIGENTE.
        c.setNumeroDocumento(s.numeroDocumento().trim());
        c.setFechaEmision(s.fechaEmision());
        c.setFechaVencimiento(s.fechaVencimiento());
        c.setEstadoValidacion("VIGENTE");
        return certificadosLote.saveAndFlush(c);
    }

    public LoteCertificacion subirSoporte(Integer idLote, Integer idCertificacion, MultipartFile archivo) {
        LoteCertificacion c = existente(idLote, idCertificacion);
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.invalido("Adjunte el archivo del certificado.");
        }
        String extension = TIPOS.get(archivo.getContentType());
        if (extension == null) {
            throw ApiException.invalido("El soporte debe ser PDF, JPG o PNG.");
        }
        String nombre = "lote" + idLote + "-cert" + idCertificacion + "-" + UUID.randomUUID() + extension;
        try (InputStream in = archivo.getInputStream()) {
            Files.createDirectories(directorio);
            Files.copy(in, directorio.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el soporte", e);
        }
        c.setUrlSoporte("certificados/" + nombre);
        return certificadosLote.saveAndFlush(c);
    }

    @Transactional(readOnly = true)
    public Soporte soporte(Integer idLote, Integer idCertificacion) {
        LoteCertificacion c = existente(idLote, idCertificacion);
        if (c.getUrlSoporte() == null || !c.getUrlSoporte().startsWith("certificados/")) {
            throw ApiException.noEncontrado("El soporte digitalizado");
        }
        Path ruta = directorio.resolve(c.getUrlSoporte().substring("certificados/".length())).normalize();
        if (!ruta.startsWith(directorio) || !Files.exists(ruta)) {
            throw ApiException.noEncontrado("El soporte digitalizado");
        }
        String nombre = ruta.getFileName().toString();
        String tipo = nombre.endsWith(".pdf") ? "application/pdf" : nombre.endsWith(".png") ? "image/png" : "image/jpeg";
        return new Soporte(new PathResource(ruta), tipo, nombre);
    }

    @Transactional(readOnly = true)
    public List<Alerta> porVencer(Integer dias) {
        int d = dias == null ? diasAlertaPorDefecto : Math.max(0, Math.min(dias, 365));
        LocalDate hoy = LocalDate.now();
        Map<Integer, String> nombres = new java.util.HashMap<>();
        catalogo.findAll().forEach(c -> nombres.put(c.getIdCertificacion(), c.getNombre()));
        return certificadosLote.porVencer(hoy, hoy.plusDays(d)).stream()
                .map(c -> new Alerta(c.getIdLote(), lotes.obtener(c.getIdLote()).getCodigoLote(),
                        nombres.get(c.getIdCertificacion()), c.getNumeroDocumento(), c.getFechaVencimiento(),
                        java.time.temporal.ChronoUnit.DAYS.between(hoy, c.getFechaVencimiento())))
                .toList();
    }

    /** Todos los dias a la 1:00 marca VENCIDO lo que ya paso su fecha (la base no lo hace sola). */
    @Scheduled(cron = "0 0 1 * * *")
    public void marcarVencidos() {
        int n = certificadosLote.marcarVencidos(LocalDate.now());
        if (n > 0) {
            log.info("{} certificado(s) marcados como VENCIDO", n);
        }
    }

    private LoteCertificacion existente(Integer idLote, Integer idCertificacion) {
        lotes.obtener(idLote);
        return certificadosLote.findById(new LoteCertificacion.Clave(idLote, idCertificacion))
                .orElseThrow(() -> ApiException.noEncontrado("El certificado en este lote"));
    }
}
