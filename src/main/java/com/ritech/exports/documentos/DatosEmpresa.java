package com.ritech.exports.documentos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exportador que aparece en la factura, la lista de empaque y la DDS. Se configura con las
 * variables EMPRESA_* (ver application.yml); lo que quede vacio no se imprime.
 */
@ConfigurationProperties(prefix = "ritech.empresa")
public record DatosEmpresa(String razonSocial, String nit, String direccion, String ciudad, String pais,
                           String email) {

    public DatosEmpresa {
        razonSocial = vacio(razonSocial, "RiTech SAS");
        nit = vacio(nit, "");
        direccion = vacio(direccion, "");
        ciudad = vacio(ciudad, "");
        pais = vacio(pais, "Colombia");
        email = vacio(email, "");
    }

    private static String vacio(String v, String porDefecto) {
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }
}
