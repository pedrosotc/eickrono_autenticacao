package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public final class ClienteTokenBackchannelPerfilKeycloak {

    private static final Duration ANTECEDENCIA_RENOVACAO = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final String urlToken;
    private final String clientId;
    private final String clientSecret;

    private volatile String tokenAtual;
    private volatile Instant tokenExpiraEm;

    public ClienteTokenBackchannelPerfilKeycloak(final RestTemplateBuilder restTemplateBuilder,
                                                 final PerfilDominioBackchannelProperties properties) {
        PerfilDominioBackchannelProperties.JwtInterno jwtInterno = Objects.requireNonNull(
                Objects.requireNonNull(properties, "properties é obrigatório").getJwtInterno(),
                "jwtInterno é obrigatório");
        Duration timeout = Objects.requireNonNullElse(jwtInterno.getTimeout(), Duration.ofSeconds(5));
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
        this.urlToken = normalizarUrlBase(jwtInterno.getUrlBase())
                + "/realms/" + obrigatorio(jwtInterno.getRealm(), "integracao.perfil.jwt-interno.realm")
                + "/protocol/openid-connect/token";
        this.clientId = obrigatorio(jwtInterno.getClientId(), "integracao.perfil.jwt-interno.client-id");
        this.clientSecret = obrigatorio(jwtInterno.getClientSecret(), "integracao.perfil.jwt-interno.client-secret");
    }

    public String obterTokenBearer() {
        Instant agora = Instant.now();
        if (StringUtils.hasText(tokenAtual) && tokenExpiraEm != null && agora.isBefore(tokenExpiraEm.minus(ANTECEDENCIA_RENOVACAO))) {
            return tokenAtual;
        }
        synchronized (this) {
            Instant referencia = Instant.now();
            if (StringUtils.hasText(tokenAtual)
                    && tokenExpiraEm != null
                    && referencia.isBefore(tokenExpiraEm.minus(ANTECEDENCIA_RENOVACAO))) {
                return tokenAtual;
            }
            JsonNode payload = restTemplate.postForObject(
                    urlToken,
                    new HttpEntity<>(corpoRequisicao(), cabecalhosFormulario()),
                    JsonNode.class
            );
            if (payload == null || !payload.hasNonNull("access_token")) {
                throw new IllegalStateException("Falha ao obter access token interno para o backchannel de perfil.");
            }
            this.tokenAtual = payload.path("access_token").asText();
            long expiresIn = payload.path("expires_in").asLong(60L);
            this.tokenExpiraEm = Instant.now().plusSeconds(expiresIn);
            return tokenAtual;
        }
    }

    private MultiValueMap<String, String> corpoRequisicao() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private HttpHeaders cabecalhosFormulario() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private static String normalizarUrlBase(final String urlBase) {
        String valor = obrigatorio(urlBase, "integracao.perfil.jwt-interno.url-base");
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    private static String obrigatorio(final String valor, final String propriedade) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalStateException(propriedade + " é obrigatório.");
        }
        return valor.trim();
    }
}
