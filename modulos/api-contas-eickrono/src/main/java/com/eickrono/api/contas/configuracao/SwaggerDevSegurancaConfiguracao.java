package com.eickrono.api.contas.configuracao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Libera o Swagger em ambiente de desenvolvimento.
 */
@Configuration
@Profile("dev")
public class SwaggerDevSegurancaConfiguracao {

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerDevSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
