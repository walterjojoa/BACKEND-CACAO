package com.ritech.exports.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SCRUM-21: una regla mal escrita bloquearia todos los lotes, por eso se valida al guardarla. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReglaServicioTest {

    @Mock
    ReglaRepositorio reglas;
    @InjectMocks
    ReglaServicio servicio;

    @BeforeEach
    void preparar() {
        when(reglas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static ReglaServicio.ReglaSolicitud regla(String codigo, String parametro, String operador, String limite) {
        return new ReglaServicio.ReglaSolicitud(codigo, "Reglamento (UE) 2023/915", null, "CAFE", parametro,
                operador, limite, "Supera el limite.", null);
    }

    @Test
    void creaUnaReglaDeLoteActiva() {
        ReglaNormativa r = servicio.crear(regla("R-UE-009", "ocratoxina_a_cafe", "<=", " 3.0 "));

        assertThat(r.getAmbito()).isEqualTo("LOTE");
        assertThat(r.getValorLimite()).isEqualTo("3.0");
        assertThat(r.getActiva()).isTrue();
    }

    @Test
    void rechazaUnParametroQueElMotorNoSabeMedir() {
        assertThatThrownBy(() -> servicio.crear(regla("R-UE-009", "plomo_cafe", "<=", "1")))
                .hasMessageContaining("Parametro desconocido");
    }

    @Test
    void elLimiteDebeSerNumericoOTrue() {
        assertThatThrownBy(() -> servicio.crear(regla("R-UE-009", "humedad_cafe_verde", "<=", "doce")))
                .hasMessageContaining("numero o 'true'");
        assertThat(servicio.crear(regla("R-UE-010", "certificado_fitosanitario_vigente", "=", "true")).getValorLimite())
                .isEqualTo("true");
    }

    @Test
    void codigoRepetidoEs409() {
        when(reglas.existsByCodigoRegla("R-UE-004")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(regla("R-UE-004", "ocratoxina_a_cafe", "<=", "5")))
                .hasMessageContaining("Ya existe");
    }

    @Test
    void elCodigoDeUnaReglaNoSeCambia() {
        ReglaNormativa r = new ReglaNormativa();
        r.setIdRegla(4);
        r.setCodigoRegla("R-UE-004");
        r.setAmbito("LOTE");
        when(reglas.findById(4)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> servicio.editar(4, regla("R-UE-099", "ocratoxina_a_cafe", "<=", "5")))
                .hasMessageContaining("no se cambia");
    }

    @Test
    void laReglaDeOrdenNoAdmiteCambiarSuCriterio() {
        ReglaNormativa r8 = new ReglaNormativa();
        r8.setIdRegla(8);
        r8.setCodigoRegla("R-UE-008");
        r8.setAmbito("ORDEN");
        r8.setParametro("eori_importador_valido");
        r8.setOperador("=");
        r8.setValorLimite("true");
        when(reglas.findById(8)).thenReturn(Optional.of(r8));

        assertThatThrownBy(() -> servicio.editar(8, regla("R-UE-008", "eori_importador_valido", "<>", "true")))
                .hasMessageContaining("ambito ORDEN");
        // Cambiar solo el texto si se permite.
        assertThat(servicio.editar(8, regla("R-UE-008", "eori_importador_valido", "=", "true")).getAmbito())
                .isEqualTo("ORDEN");
    }
}
