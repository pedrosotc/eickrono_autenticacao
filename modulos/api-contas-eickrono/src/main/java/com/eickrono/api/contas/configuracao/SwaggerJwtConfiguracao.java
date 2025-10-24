package com.eickrono.api.contas.configuracao;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configura o esquema de segurança Bearer JWT no Swagger (dev/hml).
 */
@Configuration
@Profile({"dev", "hml"})
public class SwaggerJwtConfiguracao {

    private static final String SECURITY_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI openApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
