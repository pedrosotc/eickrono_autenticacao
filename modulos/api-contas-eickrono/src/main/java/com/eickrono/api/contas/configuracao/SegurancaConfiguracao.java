package com.eickrono.api.contas.configuracao;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração de segurança, recursos OAuth2 e mTLS para a API de Contas.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({FapiProperties.class, CorsProperties.class, TlsMutuoProperties.class, SwaggerSegurancaProperties.class})
public class SegurancaConfiguracao {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http,
                                           ConversorJwtFapi conversor,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.securityMatcher("/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/contas", "/contas/*")
                        .hasAnyAuthority("SCOPE_contas:ler", "ROLE_cliente")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)));
        return http.build();
    }

    @Bean
    public ConversorJwtFapi conversorJwtFapi() {
        return new ConversorJwtFapi();
    }

    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory(TlsMutuoProperties properties) {
        if (!properties.isHabilitado()) {
            return new TomcatServletWebServerFactory();
        }
        validarCampo(properties.getKeystoreArquivo(), "Keystore obrigatório para mTLS");
        validarCampo(properties.getKeystoreSenha(), "Senha do keystore obrigatória para mTLS");
        validarCampo(properties.getTruststoreArquivo(), "Truststore obrigatório para mTLS");
        validarCampo(properties.getTruststoreSenha(), "Senha do truststore obrigatória para mTLS");

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setClientAuth(Ssl.ClientAuth.NEED);
        ssl.setKeyStore(properties.getKeystoreArquivo());
        ssl.setKeyStorePassword(properties.getKeystoreSenha());
        ssl.setTrustStore(properties.getTruststoreArquivo());
        ssl.setTrustStorePassword(properties.getTruststoreSenha());
        factory.setSsl(ssl);
        return factory;
    }

    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties resourceServerProperties,
                                 FapiProperties fapiProperties) {
        String issuerUri = Objects.requireNonNull(resourceServerProperties.getJwt().getIssuerUri(),
                "issuer-uri deve estar configurado");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
                "aud",
                aud -> {
                    if (aud instanceof String audString) {
                        return audString.equals(fapiProperties.getAudienciaEsperada());
                    }
                    if (aud instanceof Iterable<?> audLista) {
                        for (Object valor : audLista) {
                            if (valor instanceof String audValor && audValor.equals(fapiProperties.getAudienciaEsperada())) {
                                return true;
                            }
                        }
                    }
                    return false;
                });
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(fapiProperties.getToleranciaClockSkew()),
                audienceValidator
        );
        decoder.setJwtValidator(validators);
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getOrigensPermitidas());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of("jwks-cache"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1_000));
        return cacheManager;
    }

    private void validarCampo(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(mensagem);
        }
    }
}
