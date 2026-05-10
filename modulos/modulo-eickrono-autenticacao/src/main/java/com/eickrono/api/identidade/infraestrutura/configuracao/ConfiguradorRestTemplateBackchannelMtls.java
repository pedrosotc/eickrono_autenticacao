package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConfiguradorRestTemplateBackchannelMtls {

    private final TlsMutuoProperties tlsMutuoProperties;
    private final ResourceLoader resourceLoader;

    private volatile SSLContext sslContextCache;

    public ConfiguradorRestTemplateBackchannelMtls(final TlsMutuoProperties tlsMutuoProperties,
                                                   final ResourceLoader resourceLoader) {
        this.tlsMutuoProperties = Objects.requireNonNull(tlsMutuoProperties, "tlsMutuoProperties e obrigatorio");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader e obrigatorio");
    }

    public RestTemplateBuilder configurar(final RestTemplateBuilder restTemplateBuilder,
                                          final String urlBase,
                                          final Duration timeout) {
        Objects.requireNonNull(restTemplateBuilder, "restTemplateBuilder e obrigatorio");
        Duration timeoutEfetivo = Objects.requireNonNullElse(timeout, Duration.ofSeconds(5));
        if (!deveUsarMtls(urlBase)) {
            return restTemplateBuilder
                    .setConnectTimeout(timeoutEfetivo)
                    .setReadTimeout(timeoutEfetivo);
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeoutEfetivo)
                .sslContext(obterSslContext())
                .build();
        return restTemplateBuilder.requestFactory(() -> {
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(timeoutEfetivo);
            return requestFactory;
        });
    }

    private boolean deveUsarMtls(final String urlBase) {
        URI uri = URI.create(Objects.requireNonNull(urlBase, "urlBase e obrigatorio"));
        String esquema = uri.getScheme();
        if (!"https".equalsIgnoreCase(esquema)) {
            return false;
        }
        if (!tlsMutuoProperties.isHabilitado()) {
            throw new IllegalStateException("Backchannel HTTPS interno requer seguranca.mtls.habilitado=true.");
        }
        validarConfiguracaoMtls();
        return true;
    }

    private void validarConfiguracaoMtls() {
        validarTexto(tlsMutuoProperties.getKeystoreArquivo(), "seguranca.mtls.keystore-arquivo e obrigatorio.");
        validarTexto(tlsMutuoProperties.getKeystoreSenha(), "seguranca.mtls.keystore-senha e obrigatorio.");
        validarTexto(tlsMutuoProperties.getTruststoreArquivo(), "seguranca.mtls.truststore-arquivo e obrigatorio.");
        validarTexto(tlsMutuoProperties.getTruststoreSenha(), "seguranca.mtls.truststore-senha e obrigatorio.");
    }

    private SSLContext obterSslContext() {
        SSLContext sslContext = sslContextCache;
        if (sslContext != null) {
            return sslContext;
        }
        synchronized (this) {
            if (sslContextCache != null) {
                return sslContextCache;
            }
            try {
                KeyStore keyStore = carregarKeyStore(
                        tlsMutuoProperties.getKeystoreArquivo(),
                        tlsMutuoProperties.getKeystoreSenha());
                KeyStore trustStore = carregarKeyStore(
                        tlsMutuoProperties.getTruststoreArquivo(),
                        tlsMutuoProperties.getTruststoreSenha());

                KeyManagerFactory keyManagerFactory =
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, tlsMutuoProperties.getKeystoreSenha().toCharArray());

                TrustManagerFactory trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                SSLContext novoSslContext = SSLContext.getInstance("TLS");
                novoSslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
                sslContextCache = novoSslContext;
                return novoSslContext;
            } catch (GeneralSecurityException | IOException ex) {
                throw new IllegalStateException("Falha ao inicializar o SSLContext do backchannel interno com mTLS.", ex);
            }
        }
    }

    private KeyStore carregarKeyStore(final String localizacao, final String senha)
            throws GeneralSecurityException, IOException {
        Resource resource = resourceLoader.getResource(validarTexto(localizacao, "Localizacao do keystore e obrigatoria."));
        if (!resource.exists()) {
            throw new IllegalStateException("Arquivo de keystore/truststore nao encontrado: " + localizacao);
        }
        KeyStore keyStore = KeyStore.getInstance(determinarTipoKeyStore(localizacao));
        try (InputStream inputStream = resource.getInputStream()) {
            keyStore.load(inputStream, validarTexto(senha, "Senha do keystore/truststore e obrigatoria.").toCharArray());
        }
        return keyStore;
    }

    private static String determinarTipoKeyStore(final String localizacao) {
        String normalizado = localizacao.toLowerCase(Locale.ROOT);
        return normalizado.endsWith(".jks") ? "JKS" : "PKCS12";
    }

    private static String validarTexto(final String valor, final String mensagem) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalStateException(mensagem);
        }
        return valor.trim();
    }
}
