package com.eickrono.api.identidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = AplicacaoApiIdentidadeTest.Initializer.class)
class AplicacaoApiIdentidadeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OIDC_REALM_PATH = "/realms/test";
    private static final String METADATA_PATH = OIDC_REALM_PATH + "/.well-known/openid-configuration";
    private static final String JWKS_PATH = OIDC_REALM_PATH + "/protocol/openid-connect/certs";
    private static final String EXPECTED_AUDIENCE = "api-identidade-eickrono";
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.5")
            .withDatabaseName("eickrono_identidade_test")
            .withUsername("test")
            .withPassword("test");

    private static MockWebServer oidcServer;
    private static RSAKey rsaKey;
    private static String issuer;

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            iniciarPostgres();
            iniciarOidc();
            registrarPropriedades(context);
        }

        private void iniciarPostgres() {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
        }

        private void iniciarOidc() {
            if (oidcServer != null) {
                return;
            }

            rsaKey = gerarChaveJwt();
            oidcServer = new MockWebServer();
            try {
                oidcServer.start();
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao iniciar MockWebServer para OIDC", e);
            }

            HttpUrl issuerUrl = oidcServer.url(OIDC_REALM_PATH);
            issuer = issuerUrl.toString();

            String metadataJson = toJson(Map.of(
                    "issuer", issuer,
                    "jwks_uri", oidcServer.url(JWKS_PATH).toString(),
                    "id_token_signing_alg_values_supported", List.of("RS256"),
                    "subject_types_supported", List.of("public")
            ));

            String jwksJson = toJson(Map.of(
                    "keys", List.of(rsaKey.toPublicJWK().toJSONObject())
            ));

            oidcServer.setDispatcher(oidcDispatcher(metadataJson, jwksJson));
        }

        private void registrarPropriedades(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.datasource.driver-class-name=" + POSTGRES.getDriverClassName(),
                    "spring.flyway.enabled=true",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                    "fapi.seguranca.audiencia-esperada=" + EXPECTED_AUDIENCE
            ).applyTo(context.getEnvironment());
            context.addApplicationListener(new EncerramentoInfraestruturaListener());
        }
    }

    private static class EncerramentoInfraestruturaListener implements ApplicationListener<ContextClosedEvent> {
        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            encerrarInfraestrutura();
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void deveCarregarContextoEAplicarMigracoes() {
        assertThat(applicationContext).isNotNull();
        assertThat(flyway.info().current())
                .as("Flyway deve aplicar ao menos uma migração antes dos testes")
                .isNotNull();
    }

    @Test
    void deveValidarJwtEmitidoPeloOidcSimulado() throws Exception {
        String token = emitirJwtAssinado(rsaKey, rsaKey.getKeyID(), Duration.ofMinutes(15));

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("usuario-de-teste");
        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer);
        assertThat(jwt.getAudience()).contains(EXPECTED_AUDIENCE);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void deveRecusarJwtComAssinaturaNaoReconhecida() throws Exception {
        RSAKey outraChave = gerarChaveJwt();
        String token = emitirJwtAssinado(outraChave, outraChave.getKeyID(), Duration.ofMinutes(5));

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("no matching key");
    }

    @Test
    void deveRecusarJwtExpirado() throws Exception {
        Instant emissao = Instant.now().minusSeconds(3600);
        Instant expiracao = Instant.now().minusSeconds(60);
        String token = emitirJwtAssinado(rsaKey, rsaKey.getKeyID(), expiracao, emissao);

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @AfterAll
    static void encerrarInfraestrutura() {
        try {
            if (oidcServer != null) {
                oidcServer.shutdown();
                oidcServer = null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao encerrar MockWebServer do OIDC simulado", e);
        } finally {
            if (POSTGRES.isRunning()) {
                POSTGRES.stop();
            }
        }
    }

    private static RSAKey gerarChaveJwt() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("eickrono-identidade-" + UUID.randomUUID())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao gerar chave RSA para os testes de JWT", e);
        }
    }

    private static Dispatcher oidcDispatcher(String metadataJson, String jwksJson) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && (path.equals(METADATA_PATH) || path.startsWith(METADATA_PATH + "?"))) {
                    return jsonResponse(metadataJson);
                }
                if (path != null && (path.equals(JWKS_PATH) || path.startsWith(JWKS_PATH + "?"))) {
                    return jsonResponse(jwksJson);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar JSON para o servidor OIDC simulado", e);
        }
    }

    private static String emitirJwtAssinado(RSAKey key, String kid, Duration validade) throws JOSEException {
        Instant agora = Instant.now();
        Instant expiracao = agora.plus(validade);
        return emitirJwtAssinado(key, kid, expiracao, agora);
    }

    private static String emitirJwtAssinado(RSAKey key, String kid, Instant expiracao, Instant agora)
            throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject("usuario-de-teste")
                .audience(EXPECTED_AUDIENCE)
                .issueTime(Date.from(agora))
                .notBeforeTime(Date.from(agora.minusSeconds(5)))
                .expirationTime(Date.from(expiracao))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
                claims);

        JWSSigner signer = new RSASSASigner(key);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
