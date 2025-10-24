package com.eickrono.api.contas.configuracao;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuração do SpringDoc para ambientes não produtivos.
 */
@Configuration
@Profile({"dev", "hml"})
public class SwaggerConfiguracao {

    @Bean
    public GroupedOpenApi agrupamentoContas() {
        return GroupedOpenApi.builder()
                .group("contas")
                .pathsToMatch("/contas/**")
                .build();
    }
}
