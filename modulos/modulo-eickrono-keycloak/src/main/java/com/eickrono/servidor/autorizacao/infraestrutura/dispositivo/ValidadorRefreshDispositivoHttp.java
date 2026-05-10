package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Valida o device token consultando a API de identidade.
 */
final class ValidadorRefreshDispositivoHttp implements ValidadorRefreshDispositivo {

    private static final String CAMINHO_VALIDACAO_INTERNA = "/api/conta/dispositivos/token/validacao/interna";
    private static final String CAMINHO_TOKEN = "/realms/%s/protocol/openid-connect/token";
    private static final Duration ANTECEDENCIA_RENOVACAO = Duration.ofSeconds(30);

    private final ConfiguracaoValidacaoRefreshDispositivo configuracao;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String tokenAtual;
    private volatile Instant tokenExpiraEm;

    ValidadorRefreshDispositivoHttp(ConfiguracaoValidacaoRefreshDispositivo configuracao) {
        this(configuracao, criarHttpClient(configuracao), new ObjectMapper());
    }

    ValidadorRefreshDispositivoHttp(ConfiguracaoValidacaoRefreshDispositivo configuracao,
                                    HttpClient httpClient,
                                    ObjectMapper objectMapper) {
        this.configuracao = configuracao;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultadoValidacaoRefreshDispositivo validar(String usuarioSub, String deviceToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuracao.urlBaseIdentidade() + CAMINHO_VALIDACAO_INTERNA))
                    .timeout(configuracao.timeout())
                    .header("X-Eickrono-Internal-Secret", configuracao.segredoInterno())
                    .header("Authorization", "Bearer " + obterAccessToken())
                    .header("X-Device-Token", deviceToken)
                    .header("X-Usuario-Sub", usuarioSub == null ? "" : usuarioSub)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Falha ao validar device token no refresh. status=" + response.statusCode());
            }
            PayloadValidacao payload = Objects.requireNonNull(
                    objectMapper.readValue(response.body(), PayloadValidacao.class));
            return new ResultadoValidacaoRefreshDispositivo(
                    payload.valido(),
                    payload.codigo(),
                    payload.mensagem());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Validacao de device token interrompida", e);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao consultar a API de identidade", e);
        }
    }

    private String obterAccessToken() throws IOException, InterruptedException {
        Instant agora = Instant.now();
        if (tokenAtual != null && tokenExpiraEm != null && agora.isBefore(tokenExpiraEm.minus(ANTECEDENCIA_RENOVACAO))) {
            return tokenAtual;
        }
        synchronized (this) {
            Instant referencia = Instant.now();
            if (tokenAtual != null
                    && tokenExpiraEm != null
                    && referencia.isBefore(tokenExpiraEm.minus(ANTECEDENCIA_RENOVACAO))) {
                return tokenAtual;
            }

            HttpRequest requestToken = HttpRequest.newBuilder()
                    .uri(URI.create(configuracao.urlBaseKeycloak() + CAMINHO_TOKEN.formatted(configuracao.realmKeycloak())))
                    .timeout(configuracao.timeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoToken()))
                    .build();
            HttpResponse<String> responseToken = httpClient.send(requestToken, HttpResponse.BodyHandlers.ofString());
            if (responseToken.statusCode() != 200) {
                throw new IllegalStateException("Falha ao obter JWT interno para validar device token. status=" + responseToken.statusCode());
            }
            PayloadToken payloadToken = Objects.requireNonNull(
                    objectMapper.readValue(responseToken.body(), PayloadToken.class));
            if (payloadToken.accessToken() == null || payloadToken.accessToken().isBlank()) {
                throw new IllegalStateException("Resposta sem access_token no client_credentials interno.");
            }
            tokenAtual = payloadToken.accessToken();
            tokenExpiraEm = Instant.now().plusSeconds(payloadToken.expiresIn() <= 0 ? 60 : payloadToken.expiresIn());
            return tokenAtual;
        }
    }

    private String corpoToken() {
        return "grant_type=client_credentials"
                + "&client_id=" + urlEncode(configuracao.clientIdInterno())
                + "&client_secret=" + urlEncode(configuracao.clientSecretInterno());
    }

    private static String urlEncode(final String valor) {
        return URLEncoder.encode(Objects.requireNonNull(valor), StandardCharsets.UTF_8);
    }

    record PayloadValidacao(boolean valido, String codigo, String mensagem) {
    }

    record PayloadToken(String accessToken, long expiresIn) {
        @com.fasterxml.jackson.annotation.JsonCreator
        PayloadToken(
                @com.fasterxml.jackson.annotation.JsonProperty("access_token") final String accessToken,
                @com.fasterxml.jackson.annotation.JsonProperty("expires_in") final long expiresIn) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
        }
    }

    private static HttpClient criarHttpClient(final ConfiguracaoValidacaoRefreshDispositivo configuracao) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(configuracao.timeout());
        SSLContext sslContext = criarSslContextSeNecessario(configuracao);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    private static SSLContext criarSslContextSeNecessario(final ConfiguracaoValidacaoRefreshDispositivo configuracao) {
        boolean usarHttpsInterno = usaHttps(configuracao.urlBaseIdentidade());
        boolean usarHttpsKeycloak = usaHttps(configuracao.urlBaseKeycloak());
        if (!usarHttpsInterno && !usarHttpsKeycloak) {
            return null;
        }
        if (!configuracao.mtlsHabilitado()) {
            throw new IllegalStateException("Backchannel HTTPS interno requer EICKRONO_INTERNO_MTLS_HABILITADO=true.");
        }
        validarMtls(configuracao);
        try {
            KeyStore keyStore = carregarKeyStore(configuracao.mtlsKeystoreArquivo(), configuracao.mtlsKeystoreSenha());
            KeyStore trustStore = carregarKeyStore(configuracao.mtlsTruststoreArquivo(), configuracao.mtlsTruststoreSenha());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, configuracao.mtlsKeystoreSenha().toCharArray());

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao inicializar o SSLContext do backchannel interno.", ex);
        }
    }

    private static boolean usaHttps(final String urlBase) {
        return "https".equalsIgnoreCase(URI.create(Objects.requireNonNull(urlBase)).getScheme());
    }

    private static void validarMtls(final ConfiguracaoValidacaoRefreshDispositivo configuracao) {
        validarTexto(configuracao.mtlsKeystoreArquivo(), "EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO é obrigatório.");
        validarTexto(configuracao.mtlsKeystoreSenha(), "EICKRONO_INTERNO_MTLS_KEYSTORE_SENHA é obrigatório.");
        validarTexto(configuracao.mtlsTruststoreArquivo(), "EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO é obrigatório.");
        validarTexto(configuracao.mtlsTruststoreSenha(), "EICKRONO_INTERNO_MTLS_TRUSTSTORE_SENHA é obrigatório.");
    }

    private static KeyStore carregarKeyStore(final String localizacao, final String senha) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(determinarTipoKeyStore(localizacao));
        try (InputStream inputStream = Files.newInputStream(resolverPath(localizacao))) {
            keyStore.load(inputStream, senha.toCharArray());
        }
        return keyStore;
    }

    private static Path resolverPath(final String localizacao) {
        String valor = validarTexto(localizacao, "Localização do keystore/truststore é obrigatória.");
        return valor.startsWith("file:") ? Path.of(URI.create(valor)) : Path.of(valor);
    }

    private static String determinarTipoKeyStore(final String localizacao) {
        String normalizado = validarTexto(localizacao, "Localização do keystore/truststore é obrigatória.")
                .toLowerCase(Locale.ROOT);
        return normalizado.endsWith(".jks") ? "JKS" : "PKCS12";
    }

    private static String validarTexto(final String valor, final String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(mensagem);
        }
        return valor.trim();
    }
}
