package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.eickrono.api.identidade.aplicacao.servico.TokenDispositivoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração de segurança, recursos OAuth2 e mTLS.
 */
@Configuration
@EnableConfigurationProperties({FapiProperties.class, CorsProperties.class, TlsMutuoProperties.class, SwaggerSegurancaProperties.class,
        IntegracaoInternaProperties.class, AtestacaoAppProperties.class, SessaoInternaKeycloakProperties.class,
        CadastroInternoKeycloakProperties.class})
public class SegurancaConfiguracao {

    private static final String CACHE_JWKS = "jwks-cache";
    private static final long CACHE_TAMANHO_MAXIMO = 1_000L;
    private static final long CACHE_EXPIRACAO_MINUTOS = 5L;
    private static final long CORS_MAX_AGE_HORAS = 1L;
    private static final Duration CACHE_EXPIRACAO_PADRAO = Duration.ofMinutes(CACHE_EXPIRACAO_MINUTOS);
    private static final Duration CORS_MAX_AGE = Duration.ofHours(CORS_MAX_AGE_HORAS);
    private static final List<String> CORS_METODOS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    private static final List<String> CORS_CABECALHOS = List.of("Authorization", "Content-Type", "X-Device-Token");
    private static final AntPathRequestMatcher ACTUATOR_HEALTH_MATCHER = AntPathRequestMatcher.antMatcher("/actuator/health");
    private static final AntPathRequestMatcher ACTUATOR_INFO_MATCHER = AntPathRequestMatcher.antMatcher("/actuator/info");
    private static final AntPathRequestMatcher ERROR_MATCHER = AntPathRequestMatcher.antMatcher("/error");
    private static final AntPathRequestMatcher JWKS_PUBLICAS_MATCHER =
            AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/.well-known/chaves-publicas");
    private static final AntPathRequestMatcher REGISTRO_DISPOSITIVO_MATCHER =
            AntPathRequestMatcher.antMatcher("/identidade/dispositivos/registro/**");
    private static final AntPathRequestMatcher REGISTRO_DISPOSITIVO_CONTA_MATCHER =
            AntPathRequestMatcher.antMatcher("/api/conta/dispositivos/registro/**");

    @Bean
    @Order(0)
    public SecurityFilterChain internalApiSecurity(HttpSecurity http,
                                                   ConversorJwtFapi conversor) throws Exception {
                http.securityMatcher(
                        "/identidade/atestacoes/interna/**",
                        "/identidade/sessoes/interna",
                        "/identidade/cadastros/interna",
                        "/identidade/cadastros/interna/**",
                        "/identidade/perfis-sistema/interna",
                        "/identidade/perfis-sistema/interna/**",
                        "/identidade/dispositivos/token/validacao/interna",
                        "/api/conta/dispositivos/token/validacao/interna"
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)));
        return http.build();
    }

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
                        .requestMatchers(ACTUATOR_HEALTH_MATCHER, ACTUATOR_INFO_MATCHER).permitAll()
                        .requestMatchers(ERROR_MATCHER).permitAll()
                        .requestMatchers(JWKS_PUBLICAS_MATCHER).permitAll()
                        .requestMatchers(REGISTRO_DISPOSITIVO_MATCHER, REGISTRO_DISPOSITIVO_CONTA_MATCHER).permitAll()
                        .requestMatchers("/api/publica/atestacoes/**").permitAll()
                        .requestMatchers("/api/publica/cadastros/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publica/sessoes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publica/sessoes/sociais").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publica/sessoes/refresh").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/publica/sessoes/contextos-sociais-pendentes/*").permitAll()
                        .requestMatchers("/api/publica/recuperacoes-senha/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/identidade/perfil").permitAll()
                        .requestMatchers(HttpMethod.GET, "/identidade/vinculos-sociais")
                        .hasAnyAuthority("SCOPE_vinculos:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.GET, "/api/conta/redes-sociais")
                        .hasAnyAuthority("SCOPE_vinculos:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.GET, "/identidade/vinculos-organizacionais")
                        .hasAnyAuthority("SCOPE_vinculos:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.GET, "/api/conta/vinculos-organizacionais")
                        .hasAnyAuthority("SCOPE_vinculos:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/identidade/vinculos-sociais/*")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/api/conta/redes-sociais/*")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/identidade/vinculos-sociais/*/sincronizacao")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/api/conta/redes-sociais/*/sincronizacao")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.DELETE, "/identidade/vinculos-sociais/*")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.DELETE, "/api/conta/redes-sociais/*")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.PUT, "/identidade/vinculos-sociais/avatar-preferido")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.PUT, "/api/conta/avatar-preferido")
                        .hasAnyAuthority("SCOPE_vinculos:escrever", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/identidade/dispositivos/revogar")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/api/conta/dispositivos/revogar")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.GET, "/identidade/dispositivos/offline/politica")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.GET, "/api/conta/dispositivos/offline/politica")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/identidade/dispositivos/offline/eventos")
                        .hasAnyAuthority("SCOPE_identidade:ler", "ROLE_cliente")
                        .requestMatchers(HttpMethod.POST, "/api/conta/dispositivos/offline/eventos")
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
    public DeviceTokenFilter deviceTokenFilter(TokenDispositivoService tokenDispositivoService,
                                               ObjectMapper objectMapper) {
        return new DeviceTokenFilter(tokenDispositivoService, objectMapper);
    }

    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory(TlsMutuoProperties properties) {
        if (!properties.isHabilitado()) {
            return new TomcatServletWebServerFactory();
        }
        validarMtls(properties);

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        if (properties.getPortaInterna() > 0) {
            factory.addAdditionalTomcatConnectors(criarConectorInternoMtls(properties));
            return factory;
        }

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

    private void validarMtls(final TlsMutuoProperties properties) {
        validarCampo(properties.getKeystoreArquivo(), "Keystore obrigatório para mTLS");
        validarCampo(properties.getKeystoreSenha(), "Senha do keystore obrigatória para mTLS");
        validarCampo(properties.getTruststoreArquivo(), "Truststore obrigatório para mTLS");
        validarCampo(properties.getTruststoreSenha(), "Senha do truststore obrigatória para mTLS");
    }

    private Connector criarConectorInternoMtls(final TlsMutuoProperties properties) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setPort(properties.getPortaInterna());
        connector.setProperty("SSLEnabled", Boolean.TRUE.toString());

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setSslProtocol("TLS");
        sslHostConfig.setTruststoreFile(normalizarArquivoSsl(properties.getTruststoreArquivo()));
        sslHostConfig.setTruststorePassword(properties.getTruststoreSenha());
        sslHostConfig.setTruststoreType(determinarTipoStore(properties.getTruststoreArquivo()));
        sslHostConfig.setCertificateVerification("required");

        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(normalizarArquivoSsl(properties.getKeystoreArquivo()));
        certificate.setCertificateKeystorePassword(properties.getKeystoreSenha());
        certificate.setCertificateKeystoreType(determinarTipoStore(properties.getKeystoreArquivo()));
        sslHostConfig.addCertificate(certificate);
        connector.addSslHostConfig(sslHostConfig);
        return connector;
    }

    private String normalizarArquivoSsl(final String localizacao) {
        String valor = Objects.requireNonNull(localizacao, "Arquivo SSL é obrigatório").trim();
        if (valor.startsWith("file:")) {
            return Path.of(URI.create(valor)).toString();
        }
        return valor;
    }

    private String determinarTipoStore(final String localizacao) {
        String normalizado = normalizarArquivoSsl(localizacao).toLowerCase(Locale.ROOT);
        return normalizado.endsWith(".jks") ? "JKS" : "PKCS12";
    }

    private NimbusJwtDecoder criarDecoder(OAuth2ResourceServerProperties.Jwt jwtProperties) {
        String issuerUri = jwtProperties.getIssuerUri();
        String jwkSetUri = jwtProperties.getJwkSetUri();

        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        if (issuerUri != null && !issuerUri.isBlank()) {
            try {
                return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Falha ao inicializar JwtDecoder com issuer-uri configurado e nenhum jwk-set-uri utilizável.", ex);
            }
        }
        throw new IllegalStateException("Configuração inválida para JwtDecoder: defina issuer-uri ou jwk-set-uri.");
    }
}
