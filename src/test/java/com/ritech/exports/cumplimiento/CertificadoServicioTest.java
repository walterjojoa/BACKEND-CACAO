package com.ritech.exports.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ritech.exports.Sesion;
import com.ritech.exports.lotes.LoteServicio;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

/** SCRUM-20 certificados con vigencia y soporte; SCRUM-23 alertas de vencimiento. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CertificadoServicioTest {

    @Mock
    LoteCertificacionRepositorio certificadosLote;
    @Mock
    CertificacionRepositorio catalogo;
    @Mock
    LoteServicio lotes;
    @TempDir
    Path almacenamiento;

    private CertificadoServicio servicio;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void preparar() {
        servicio = new CertificadoServicio(certificadosLote, catalogo, lotes, almacenamiento.toString(), 30);
        when(catalogo.existsById(1)).thenReturn(true);
        when(lotes.obtener(5)).thenReturn(Sesion.lote(5, "REGISTRADO", "1000"));
        when(certificadosLote.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CertificadoServicio.CertificadoSolicitud cert(LocalDate emision, LocalDate vencimiento) {
        return new CertificadoServicio.CertificadoSolicitud(1, " FITO-2026-8891 ", emision, vencimiento);
    }

    @Test
    void cargaUnCertificadoVigente() {
        LoteCertificacion c = servicio.cargar(5, cert(hoy.minusDays(5), hoy.plusDays(120)));

        assertThat(c.getEstadoValidacion()).isEqualTo("VIGENTE");
        assertThat(c.getNumeroDocumento()).isEqualTo("FITO-2026-8891");
    }

    @Test
    void elVencimientoDebeSerPosteriorALaEmision() { // CP-029
        assertThatThrownBy(() -> servicio.cargar(5, cert(hoy.minusDays(1), hoy.minusDays(10))))
                .hasMessageContaining("posterior a la fecha de emision");
    }

    @Test
    void noSeCargaUnCertificadoVencido() { // CP-028
        assertThatThrownBy(() -> servicio.cargar(5, cert(hoy.minusYears(1), hoy)))
                .hasMessageContaining("ya vencido");
    }

    @Test
    void laEmisionNoPuedeSerFutura() {
        assertThatThrownBy(() -> servicio.cargar(5, cert(hoy.plusDays(1), hoy.plusDays(90))))
                .hasMessageContaining("emision no puede ser futura");
    }

    @Test
    void cargarOtraVezElMismoTipoEsUnaRenovacion() {
        LoteCertificacion vencido = new LoteCertificacion();
        vencido.setIdLote(5);
        vencido.setIdCertificacion(1);
        vencido.setEstadoValidacion("VENCIDO");
        when(certificadosLote.findById(any())).thenReturn(Optional.of(vencido));

        LoteCertificacion c = servicio.cargar(5, cert(hoy, hoy.plusDays(180)));

        assertThat(c).isSameAs(vencido);
        assertThat(c.getEstadoValidacion()).isEqualTo("VIGENTE");
        assertThat(c.getFechaVencimiento()).isEqualTo(hoy.plusDays(180));
    }

    @Test
    void guardaElSoportePdfYLoPuedeDevolver() throws Exception {
        LoteCertificacion existente = new LoteCertificacion();
        existente.setIdLote(5);
        existente.setIdCertificacion(1);
        when(certificadosLote.findById(any())).thenReturn(Optional.of(existente));

        LoteCertificacion c = servicio.subirSoporte(5, 1,
                new MockMultipartFile("archivo", "fito.pdf", "application/pdf", "%PDF-1.7 prueba".getBytes()));

        assertThat(c.getUrlSoporte()).startsWith("certificados/lote5-cert1-").endsWith(".pdf");
        CertificadoServicio.Soporte s = servicio.soporte(5, 1);
        assertThat(s.tipo()).isEqualTo("application/pdf");
        assertThat(Files.readString(s.archivo().getFile().toPath())).isEqualTo("%PDF-1.7 prueba");
    }

    @Test
    void rechazaSoportesQueNoSonPdfNiImagen() {
        LoteCertificacion existente = new LoteCertificacion();
        when(certificadosLote.findById(any())).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.subirSoporte(5, 1,
                new MockMultipartFile("archivo", "virus.exe", "application/octet-stream", new byte[] {1})))
                .hasMessageContaining("PDF, JPG o PNG");
    }

    @Test
    void unaRutaManipuladaNoSaleDeLaCarpetaDeSoportes() {
        LoteCertificacion c = new LoteCertificacion();
        c.setUrlSoporte("certificados/../../secreto.txt");
        when(certificadosLote.findById(any())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> servicio.soporte(5, 1)).hasMessageContaining("no existe");
    }

    @Test
    void lasAlertasCalculanLosDiasRestantes() {
        LoteCertificacion c = new LoteCertificacion();
        c.setIdLote(5);
        c.setIdCertificacion(1);
        c.setNumeroDocumento("FITO-1");
        c.setFechaVencimiento(hoy.plusDays(12));
        when(certificadosLote.porVencer(eq(hoy), eq(hoy.plusDays(30)))).thenReturn(List.of(c));

        List<CertificadoServicio.Alerta> alertas = servicio.porVencer(null);

        assertThat(alertas).singleElement().satisfies(a -> {
            assertThat(a.diasRestantes()).isEqualTo(12);
            assertThat(a.codigoLote()).isEqualTo("CAF-2026-0005");
        });
    }
}
