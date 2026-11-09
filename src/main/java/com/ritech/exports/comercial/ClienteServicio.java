package com.ritech.exports.comercial;

import com.ritech.exports.comun.ApiException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClienteServicio {

    public record ClienteSolicitud(
            @NotBlank @Size(max = 160) String razonSocial,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}[A-Za-z0-9]{12,15}$",
                    message = "EORI invalido: dos letras mayusculas de pais y 12 a 15 caracteres alfanumericos")
            String numeroEori,
            @Size(max = 25) String nitVat,
            @NotNull Integer idPais,
            @NotBlank @Size(max = 80) String ciudad,
            @Size(max = 200) String direccion,
            @NotBlank @Email @Size(max = 120) String emailContacto,
            @Pattern(regexp = "ACTIVO|INACTIVO") String estado) {
    }

    private final ClienteRepositorio clientes;
    private final PaisRepositorio paises;

    public ClienteServicio(ClienteRepositorio clientes, PaisRepositorio paises) {
        this.clientes = clientes;
        this.paises = paises;
    }

    @Transactional(readOnly = true)
    public List<ClienteUe> buscar(Integer idPais, String razonSocial, String estado) {
        return clientes.buscar(idPais, vacioANulo(razonSocial), vacioANulo(estado));
    }

    @Transactional(readOnly = true)
    public ClienteUe obtener(Integer id) {
        return clientes.findById(id).orElseThrow(() -> ApiException.noEncontrado("El cliente"));
    }

    public ClienteUe crear(ClienteSolicitud s) {
        if (clientes.existsByNumeroEori(s.numeroEori())) {
            throw ApiException.conflicto("Ya existe un cliente con el EORI " + s.numeroEori() + ".");
        }
        ClienteUe c = new ClienteUe();
        copiar(s, c);
        if (c.getEstado() == null) {
            c.setEstado("ACTIVO");
        }
        return clientes.saveAndFlush(c);
    }

    public ClienteUe editar(Integer id, ClienteSolicitud s) {
        ClienteUe c = obtener(id);
        if (!c.getNumeroEori().equals(s.numeroEori()) && clientes.existsByNumeroEori(s.numeroEori())) {
            throw ApiException.conflicto("Ya existe un cliente con el EORI " + s.numeroEori() + ".");
        }
        copiar(s, c);
        return clientes.saveAndFlush(c);
    }

    private void copiar(ClienteSolicitud s, ClienteUe c) {
        Pais pais = paises.findById(s.idPais()).orElseThrow(() -> ApiException.invalido("El pais no existe."));
        // La base tambien lo impide (trg_clientes_pais_bi/bu); aqui se da un mensaje mas temprano.
        if (!Boolean.TRUE.equals(pais.getPerteneceUe())) {
            throw ApiException.invalido("El pais del importador debe ser miembro de la Union Europea.");
        }
        c.setRazonSocial(s.razonSocial().trim());
        c.setNumeroEori(s.numeroEori());
        c.setNitVat(vacioANulo(s.nitVat()));
        c.setIdPais(s.idPais());
        c.setCiudad(s.ciudad().trim());
        c.setDireccion(vacioANulo(s.direccion()));
        c.setEmailContacto(s.emailContacto().trim());
        if (s.estado() != null) {
            c.setEstado(s.estado());
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
