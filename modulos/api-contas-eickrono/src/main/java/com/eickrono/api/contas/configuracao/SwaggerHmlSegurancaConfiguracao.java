package com.eickrono.api.contas.configuracao;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Proteção adicional do Swagger em homologação com Basic Auth e lista de IPs.
 */
@Configuration
@Profile("hml")
@EnableConfigurationProperties(SwaggerSegurancaProperties.class)
public class SwaggerHmlSegurancaConfiguracao {

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerSecurity(HttpSecurity http,
                                               SwaggerSegurancaProperties properties,
                                               UserDetailsService swaggerUserDetailsService) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .userDetailsService(swaggerUserDetailsService)
                .addFilterBefore(new FiltroWhitelistIp(List.copyOf(properties.getIpsPermitidos())),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService swaggerUserDetailsService(SwaggerSegurancaProperties properties) {
        return new InMemoryUserDetailsManager(
                User.withUsername(properties.getUsuario())
                        .password("{noop}" + properties.getSenha())
                        .roles("DOCUMENTACAO")
                        .build());
    }
}
