package com.eickrono.api.identidade.infraestrutura.configuracao;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuração do SpringDoc para ambientes não produtivos.
 */
@Configuration
@Profile({"dev", "stg", "hml"})
public class SwaggerConfiguracao {

    @Bean
    public GroupedOpenApi agrupamentoIdentidade() {
        return GroupedOpenApi.builder()
                .group("identidade")
                .pathsToMatch("/identidade/**", "/api/conta/**")
                .build();
    }

    @Bean
    public GroupedOpenApi agrupamentoOperacoesInternas() {
        return GroupedOpenApi.builder()
                .group("operacoes-internas")
                .pathsToMatch("/api/interna/**")
                .build();
    }
}
