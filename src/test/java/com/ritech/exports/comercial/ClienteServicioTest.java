package com.ritech.exports.comercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ritech.exports.comun.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-11: registro de importadores de la UE. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteServicioTest {

    @Mock
    ClienteRepositorio clientes;
    @Mock
    PaisRepositorio paises;
    @InjectMocks
    ClienteServicio servicio;

    private static Pais pais(int id, String iso, boolean ue) {
        Pais p = new Pais();
        ReflectionTestUtils.setField(p, "idPais", id);
        ReflectionTestUtils.setField(p, "codigoIso2", iso);
        ReflectionTestUtils.setField(p, "perteneceUe", ue);
        return p;
    }

    private static ClienteServicio.ClienteSolicitud solicitud(int idPais, String eori) {
        return new ClienteServicio.ClienteSolicitud("  Europa Coffee GmbH ", eori, "", idPais, "Hamburgo", null,
                "compras@europa.example", null);
    }

    @BeforeEach
    void paisesDelScript() {
        when(paises.findById(11)).thenReturn(Optional.of(pais(11, "DE", true)));
        when(paises.findById(28)).thenReturn(Optional.of(pais(28, "CO", false)));
        when(clientes.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void creaElClienteActivoYLimpiaLosTextos() {
        ClienteUe c = servicio.crear(solicitud(11, "DE123456789012345"));

        assertThat(c.getEstado()).isEqualTo("ACTIVO");
        assertThat(c.getRazonSocial()).isEqualTo("Europa Coffee GmbH");
        assertThat(c.getNitVat()).as("vacio se guarda como NULL").isNull();
    }

    @Test
    void rechazaUnPaisQueNoEsDeLaUe() {
        assertThatThrownBy(() -> servicio.crear(solicitud(28, "CO123456789012345")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("miembro de la Union Europea");
        verify(clientes, never()).saveAndFlush(any());
    }

    @Test
    void rechazaUnEoriRepetido() {
        when(clientes.existsByNumeroEori("DE123456789012345")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(solicitud(11, "DE123456789012345")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getEstado().value()).isEqualTo(409);
    }

    @Test
    void editarConservaElEoriPropioSinMarcarloComoDuplicado() {
        ClienteUe existente = new ClienteUe();
        existente.setIdCliente(1);
        existente.setNumeroEori("DE123456789012345");
        existente.setEstado("ACTIVO");
        when(clientes.findById(1)).thenReturn(Optional.of(existente));
        when(clientes.existsByNumeroEori("DE123456789012345")).thenReturn(true);

        ClienteUe c = servicio.editar(1, solicitud(11, "DE123456789012345"));

        assertThat(c.getCiudad()).isEqualTo("Hamburgo");
    }
}
