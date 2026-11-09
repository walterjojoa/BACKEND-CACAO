package com.ritech.exports.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI documentacion() {
        return new OpenAPI()
                .info(new Info()
                        .title("RiTech Exports API")
                        .version("0.1.0")
                        .description("""
                                Gestion de exportaciones de cafe y cacao hacia la UE: trazabilidad EUDR, \
                                control de calidad, cumplimiento normativo, ordenes y logistica.

                                1. POST /api/auth/login con correo y contrasena.
                                2. Boton **Authorize** y pegar el token."""))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
