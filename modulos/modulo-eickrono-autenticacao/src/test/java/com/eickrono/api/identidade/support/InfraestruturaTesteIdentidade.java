package com.eickrono.api.identidade.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.testcontainers.containers.PostgreSQLContainer;

public final class InfraestruturaTesteIdentidade {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_POSTGRES_IMAGE = "postgres:15.5";
    private static final String DEFAULT_POSTGRES_DATABASE = "eickrono_identidade_test";
    private static final String DEFAULT_POSTGRES_USERNAME = "test";
    private static final String DEFAULT_POSTGRES_PASSWORD = "test";
    private static final String DEFAULT_LOCAL_POSTGRES_HOST = "localhost";
    private static final String DEFAULT_LOCAL_POSTGRES_PORT = "5432";
    private static final String DEFAULT_LOCAL_POSTGRES_DATABASE = "eickrono_dev";
    private static final String DEFAULT_LOCAL_POSTGRES_USERNAME = "eickrono";
    private static final String DEFAULT_LOCAL_POSTGRES_PASSWORD = "senhaLocalDev";
    private static final String OIDC_REALM_PATH = "/realms/test";
    private static final String METADATA_PATH = OIDC_REALM_PATH + "/.well-known/openid-configuration";
    private static final String JWKS_PATH = OIDC_REALM_PATH + "/protocol/openid-connect/certs";
    private static final String EXPECTED_AUDIENCE = "eickrono-autenticacao";
    private static PostgreSQLContainer<?> postgres;
    private static String jdbcUrl;
    private static String jdbcUsername;
    private static String jdbcPassword;
    private static String jdbcDriverClassName;
    private static String postgresSchemaTemporario;
    private static MockWebServer oidcServer;
    private static RSAKey rsaKey;
    private static String issuer;

    private InfraestruturaTesteIdentidade() {
    }

    public static final class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@NonNull ConfigurableApplicationContext context) {
            iniciarPostgres();
            iniciarOidc();
            List<String> propriedades = new ArrayList<>();
            propriedades.add("spring.datasource.url=" + jdbcUrl);
            propriedades.add("spring.datasource.username=" + jdbcUsername);
            propriedades.add("spring.datasource.password=" + jdbcPassword);
            propriedades.add("spring.datasource.driver-class-name=" + jdbcDriverClassName);
            propriedades.add("spring.flyway.enabled=true");
            propriedades.add("spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer);
            propriedades.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + oidcServer.url(JWKS_PATH));
            propriedades.add("fapi.seguranca.audiencia-esperada=" + EXPECTED_AUDIENCE);
            if (postgresSchemaTemporario != null) {
                propriedades.add("spring.jpa.properties.hibernate.default_schema=" + postgresSchemaTemporario);
                propriedades.add("spring.flyway.default-schema=" + postgresSchemaTemporario);
                propriedades.add("spring.flyway.schemas=" + postgresSchemaTemporario);
            }
            TestPropertyValues.of(propriedades).applyTo(context.getEnvironment());
            context.addApplicationListener(new EncerramentoInfraestruturaListener());
        }
    }

    public static final class EncerramentoInfraestruturaListener implements ApplicationListener<ContextClosedEvent> {
        @Override
        public void onApplicationEvent(@NonNull ContextClosedEvent event) {
            encerrarInfraestrutura();
        }
    }

    public static void encerrarInfraestrutura() {
        RuntimeException falha = null;
        try {
            if (oidcServer != null) {
                oidcServer.shutdown();
                oidcServer = null;
            }
        } catch (IOException e) {
            falha = new IllegalStateException("Falha ao encerrar MockWebServer do OIDC simulado", e);
        } finally {
            if (postgres != null) {
                try {
                    postgres.close();
                } catch (Exception e) {
                    RuntimeException erroPostgres = new IllegalStateException(
                            "Falha ao encerrar container PostgreSQL de testes",
                            e);
                    if (falha == null) {
                        falha = erroPostgres;
                    } else {
                        falha.addSuppressed(erroPostgres);
                    }
                } finally {
                    postgres = null;
                }
            }
            try {
                encerrarPostgresLocal();
            } catch (RuntimeException e) {
                if (falha == null) {
                    falha = e;
                } else {
                    falha.addSuppressed(e);
                }
            }
        }
        if (falha != null) {
            throw falha;
        }
    }

    public static RSAKey obterRsaKey() {
        if (rsaKey == null) {
            iniciarOidc();
        }
        return rsaKey;
    }

    public static String obterIssuer() {
        if (issuer == null) {
            iniciarOidc();
        }
        return issuer;
    }

    private static void iniciarPostgres() {
        if (jdbcUrl != null) {
            return;
        }
        if (devePreferirPostgresLocal()) {
            iniciarPostgresLocal(null);
            return;
        }

        RuntimeException falhaContainer = null;
        if (postgres == null) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(obterVariavelAmbiente(
                    "EICKRONO_TEST_POSTGRES_IMAGE",
                    DEFAULT_POSTGRES_IMAGE));
            container.withDatabaseName(obterVariavelAmbiente(
                    "EICKRONO_TEST_POSTGRES_DB_IDENTIDADE",
                    obterVariavelAmbiente("POSTGRES_DB", DEFAULT_POSTGRES_DATABASE)));
            container.withUsername(obterVariavelAmbiente("POSTGRES_USER", DEFAULT_POSTGRES_USERNAME));
            container.withPassword(obterVariavelAmbiente("POSTGRES_PASSWORD", DEFAULT_POSTGRES_PASSWORD));
            postgres = container;
        }
        try {
            if (!postgres.isRunning()) {
                postgres.start();
            }
            jdbcUrl = postgres.getJdbcUrl();
            jdbcUsername = postgres.getUsername();
            jdbcPassword = postgres.getPassword();
            jdbcDriverClassName = postgres.getDriverClassName();
            return;
        } catch (RuntimeException e) {
            falhaContainer = e;
            if (postgres != null) {
                try {
                    postgres.close();
                } catch (Exception ignored) {
                    // Ignorado porque o erro principal da tentativa com Testcontainers
                    // sera anexado ao fallback local caso ele tambem falhe.
                } finally {
                    postgres = null;
                }
            }
        }

        iniciarPostgresLocal(falhaContainer);
    }

    private static void iniciarOidc() {
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

    private static String obterVariavelAmbiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        return valor;
    }

    private static void iniciarPostgresLocal(RuntimeException falhaContainer) {
        String host = obterVariavelAmbiente("EICKRONO_TEST_POSTGRES_HOST", DEFAULT_LOCAL_POSTGRES_HOST);
        String port = obterVariavelAmbiente("EICKRONO_TEST_POSTGRES_PORT", DEFAULT_LOCAL_POSTGRES_PORT);
        String database = obterVariavelAmbiente(
                "EICKRONO_TEST_POSTGRES_DB_LOCAL",
                obterVariavelAmbiente("POSTGRES_DB", DEFAULT_LOCAL_POSTGRES_DATABASE));
        String username = obterVariavelAmbiente(
                "EICKRONO_TEST_POSTGRES_USER",
                obterVariavelAmbiente("POSTGRES_USER", DEFAULT_LOCAL_POSTGRES_USERNAME));
        String password = obterVariavelAmbiente(
                "EICKRONO_TEST_POSTGRES_PASSWORD",
                obterVariavelAmbiente("POSTGRES_PASSWORD", DEFAULT_LOCAL_POSTGRES_PASSWORD));
        String schema = "teste_autenticacao_" + UUID.randomUUID().toString().replace("-", "");
        String baseJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        try (Connection connection = DriverManager.getConnection(baseJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists \"" + schema + "\"");
        } catch (SQLException e) {
            IllegalStateException falhaLocal = new IllegalStateException(
                    "Falha ao preparar schema temporario no PostgreSQL local para os testes",
                    e);
            if (falhaContainer != null) {
                falhaLocal.addSuppressed(falhaContainer);
            }
            throw falhaLocal;
        }

        jdbcUrl = baseJdbcUrl + "?currentSchema=" + schema;
        jdbcUsername = username;
        jdbcPassword = password;
        jdbcDriverClassName = "org.postgresql.Driver";
        postgresSchemaTemporario = schema;
    }

    private static void encerrarPostgresLocal() {
        if (postgresSchemaTemporario == null) {
            jdbcUrl = null;
            jdbcUsername = null;
            jdbcPassword = null;
            jdbcDriverClassName = null;
            return;
        }

        String host = obterVariavelAmbiente("EICKRONO_TEST_POSTGRES_HOST", DEFAULT_LOCAL_POSTGRES_HOST);
        String port = obterVariavelAmbiente("EICKRONO_TEST_POSTGRES_PORT", DEFAULT_LOCAL_POSTGRES_PORT);
        String database = obterVariavelAmbiente(
                "EICKRONO_TEST_POSTGRES_DB_LOCAL",
                obterVariavelAmbiente("POSTGRES_DB", DEFAULT_LOCAL_POSTGRES_DATABASE));
        String username = obterVariavelAmbiente(
                "EICKRONO_TEST_POSTGRES_USER",
                obterVariavelAmbiente("POSTGRES_USER", DEFAULT_LOCAL_POSTGRES_USERNAME));
        String password = obterVariavelAmbiente(
                "EICKRONO_TEST_POSTGRES_PASSWORD",
                obterVariavelAmbiente("POSTGRES_PASSWORD", DEFAULT_LOCAL_POSTGRES_PASSWORD));
        String baseJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        try (Connection connection = DriverManager.getConnection(baseJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists \"" + postgresSchemaTemporario + "\" cascade");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao remover schema temporario dos testes de autenticacao", e);
        } finally {
            postgresSchemaTemporario = null;
            jdbcUrl = null;
            jdbcUsername = null;
            jdbcPassword = null;
            jdbcDriverClassName = null;
        }
    }

    private static boolean devePreferirPostgresLocal() {
        return Boolean.parseBoolean(obterVariavelAmbiente("EICKRONO_TEST_PREFER_LOCAL_POSTGRES", "false"));
    }
}
