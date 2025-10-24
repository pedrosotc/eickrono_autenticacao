package com.eickrono.api.identidade.configuracao;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração de segurança, recursos OAuth2 e mTLS.
 */
@Configuration
@EnableConfigurationProperties({FapiProperties.class, CorsProperties.class, TlsMutuoProperties.class, SwaggerSegurancaProperties.class})
public class SegurancaConfiguracao {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegurancaConfiguracao.class);
    private static final String CACHE_JWKS = "jwks-cache";
    private static final long CACHE_TAMANHO_MAXIMO = 1_000L;
    private static final long CACHE_EXPIRACAO_MINUTOS = 5L;
    private static final long CORS_MAX_AGE_HORAS = 1L;
    private static final Duration CACHE_EXPIRACAO_PADRAO = Duration.ofMinutes(CACHE_EXPIRACAO_MINUTOS);
    private static final Duration CORS_MAX_AGE = Duration.ofHours(CORS_MAX_AGE_HORAS);
    private static final List<String> CORS_METODOS = List.of("GET", "POST", "OPTIONS");
    private static final List<String> CORS_CABECALHOS = List.of("Authorization", "Content-Type", "X-Device-Token");

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http,
                                           ConversorJwtFapi conversor,
                                           CorsConfigurationSource corsConfigurationSource,
                                           DeviceTokenFilter deviceTokenFilter) throws Exception {
        http.securityMatcher("/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/chaves-publicas").permitAll()
                        .requestMatchers("/identidade/dispositivos/registro/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/identidade/perfil")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.GET, "/identidade/vinculos-sociais")
                        .hasAnyAuthority("SCOPE_vinculos:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/identidade/vinculos-sociais")
                        .hasAuthority("SCOPE_vinculos:escrever")
                        .requestMatchers(HttpMethod.POST, "/identidade/dispositivos/revogar")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)));
        http.addFilterAfter(deviceTokenFilter, BearerTokenAuthenticationFilter.class);
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
        NimbusJwtDecoder decoder = criarDecoder(resourceServerProperties.getJwt());

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
        configuration.setAllowedMethods(CORS_METODOS);
        configuration.setAllowedHeaders(CORS_CABECALHOS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of(CACHE_JWKS));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRACAO_PADRAO)
                .maximumSize(CACHE_TAMANHO_MAXIMO));
        return cacheManager;
    }

    private void validarCampo(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(mensagem);
        }
    }

    private NimbusJwtDecoder criarDecoder(OAuth2ResourceServerProperties.Jwt jwtProperties) {
        String issuerUri = jwtProperties.getIssuerUri();
        String jwkSetUri = jwtProperties.getJwkSetUri();

        if (issuerUri != null && !issuerUri.isBlank()) {
            try {
                return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            } catch (IllegalArgumentException ex) {
                if (jwkSetUri == null || jwkSetUri.isBlank()) {
                    throw new IllegalStateException("Falha ao inicializar JwtDecoder com issuer-uri e nenhum jwk-set-uri configurado.", ex);
                }
                LOGGER.warn("Falha ao inicializar JwtDecoder com issuer '{}'. Tentando jwk-set-uri.", issuerUri, ex);
            }
        }
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        throw new IllegalStateException("Configuração inválida para JwtDecoder: defina issuer-uri ou jwk-set-uri.");
    }
}
