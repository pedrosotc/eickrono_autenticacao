package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.infraestrutura.configuracao.SessaoInternaKeycloakProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AutenticacaoSessaoInternaServico {

    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    private static final String GRANT_TYPE_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    private static final String TOKEN_TYPE_REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token";
    private static final String TOKEN_TYPE_ID_TOKEN = "urn:ietf:params:oauth:token-type:id_token";
    private static final String CAMINHO_TOKEN = "/realms/%s/protocol/openid-connect/token";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SessaoInternaKeycloakProperties properties;
    private final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;

    public AutenticacaoSessaoInternaServico(final RestTemplateBuilder restTemplateBuilder,
                                            final ObjectMapper objectMapper,
                                            final SessaoInternaKeycloakProperties properties,
                                            final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.clienteAdministracaoCadastroKeycloak = Objects.requireNonNull(
                clienteAdministracaoCadastroKeycloak,
                "clienteAdministracaoCadastroKeycloak é obrigatório"
        );
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(this.properties.getTimeout())
                .setReadTimeout(this.properties.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper é obrigatório");
    }

    public SessaoInternaAutenticada autenticar(final String login, final String senha) {
        String loginNormalizado = Objects.requireNonNull(login, "login é obrigatório").trim().toLowerCase(Locale.ROOT);
        String senhaEfetiva = prepararSenhaEfetiva(loginNormalizado, Objects.requireNonNull(senha, "senha é obrigatória"));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_PASSWORD);
        form.add("client_id", properties.getClientId());
        if (StringUtils.hasText(properties.getClientSecret())) {
            form.add("client_secret", properties.getClientSecret());
        }
        if (StringUtils.hasText(properties.getScope())) {
            form.add("scope", properties.getScope());
        }
        form.add("username", loginNormalizado);
        form.add("password", senhaEfetiva);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = executarChamadaToken(form, headers);

        if (response.getStatusCode().is2xxSuccessful()) {
            return lerSessaoAutenticada(response.getBody());
        }

        throw traduzirErro(response);
    }

    public SessaoInternaAutenticada renovar(final String refreshToken, final String tokenDispositivo) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_REFRESH_TOKEN);
        form.add("client_id", properties.getClientId());
        if (StringUtils.hasText(properties.getClientSecret())) {
            form.add("client_secret", properties.getClientSecret());
        }
        form.add("refresh_token", Objects.requireNonNull(refreshToken, "refreshToken é obrigatório"));
        if (StringUtils.hasText(tokenDispositivo)) {
            form.add("device_token", tokenDispositivo);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = executarChamadaToken(form, headers);

        if (response.getStatusCode().is2xxSuccessful()) {
            return lerSessaoAutenticada(response.getBody());
        }

        throw traduzirErro(response);
    }

    public SessaoInternaAutenticada autenticarSocial(final String provedor, final String tokenExterno) {
        ParametrosTrocaTokenSocial parametrosTroca = resolverParametrosTrocaTokenSocial(provedor);
        String clientIdTokenExchange = resolverClientIdTokenExchange();
        String clientSecretTokenExchange = resolverClientSecretTokenExchange();
        String audienceTokenExchange = resolverAudienceTokenExchange();
        String scopeTokenExchange = resolverScopeTokenExchange();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_TOKEN_EXCHANGE);
        form.add("client_id", clientIdTokenExchange);
        if (StringUtils.hasText(clientSecretTokenExchange)) {
            form.add("client_secret", clientSecretTokenExchange);
        }
        if (StringUtils.hasText(audienceTokenExchange)) {
            form.add("audience", audienceTokenExchange);
        }
        if (StringUtils.hasText(scopeTokenExchange)) {
            form.add("scope", scopeTokenExchange);
        }
        form.add("requested_token_type", TOKEN_TYPE_REFRESH_TOKEN);
        if (StringUtils.hasText(parametrosTroca.issuer())) {
            form.add("subject_issuer", parametrosTroca.issuer());
        }
        form.add("subject_token_type", parametrosTroca.subjectTokenType());
        form.add("subject_token", Objects.requireNonNull(tokenExterno, "tokenExterno é obrigatório"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = executarChamadaToken(form, headers);

        if (response.getStatusCode().is2xxSuccessful()) {
            return lerSessaoAutenticada(response.getBody());
        }

        throw traduzirErro(response);
    }

    private String montarUrlToken() {
        return properties.getUrlBase() + CAMINHO_TOKEN.formatted(properties.getRealm());
    }

    private String prepararSenhaEfetiva(final String loginNormalizado, final String senhaPura) {
        return clienteAdministracaoCadastroKeycloak.buscarUsuarioPorEmail(loginNormalizado)
                .map(UsuarioCadastroKeycloakExistente::createdTimestamp)
                .filter(createdTimestamp -> createdTimestamp > 0L)
                .map(createdTimestamp -> DerivacaoSenhaKeycloak.derivar(
                        senhaPura,
                        createdTimestamp,
                        properties.getPasswordPepper()))
                .orElse(senhaPura);
    }

    private ParametrosTrocaTokenSocial resolverParametrosTrocaTokenSocial(final String provedor) {
        String provedorNormalizado = Objects.requireNonNull(provedor, "provedor é obrigatório")
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (provedorNormalizado) {
            case "google" -> new ParametrosTrocaTokenSocial("google", TOKEN_TYPE_ACCESS_TOKEN);
            case "apple" -> new ParametrosTrocaTokenSocial("apple", TOKEN_TYPE_ID_TOKEN);
            default -> throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Provedor social não suportado para autenticação nativa."
            );
        };
    }

    private String resolverClientIdTokenExchange() {
        if (StringUtils.hasText(properties.getTokenExchangeClientId())) {
            return properties.getTokenExchangeClientId();
        }
        return properties.getClientId();
    }

    private String resolverClientSecretTokenExchange() {
        if (StringUtils.hasText(properties.getTokenExchangeClientSecret())) {
            return properties.getTokenExchangeClientSecret();
        }
        return properties.getClientSecret();
    }

    private String resolverAudienceTokenExchange() {
        if (StringUtils.hasText(properties.getTokenExchangeAudience())) {
            return properties.getTokenExchangeAudience();
        }
        return properties.getClientId();
    }

    private String resolverScopeTokenExchange() {
        if (StringUtils.hasText(properties.getTokenExchangeScope())) {
            return properties.getTokenExchangeScope();
        }
        return properties.getScope();
    }

    private ResponseEntity<String> executarChamadaToken(final LinkedMultiValueMap<String, String> form,
                                                        final HttpHeaders headers) {
        return restTemplate.exchange(
                URI.create(montarUrlToken()),
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                String.class
        );
    }

    private SessaoInternaAutenticada lerSessaoAutenticada(final String body) {
        try {
            JsonNode payload = objectMapper.readTree(Objects.requireNonNullElse(body, "{}"));
            return new SessaoInternaAutenticada(
                    true,
                    payload.path("token_type").asText("Bearer"),
                    payload.path("access_token").asText(null),
                    payload.path("refresh_token").asText(null),
                    payload.path("expires_in").asLong(0L)
            );
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Falha ao interpretar a resposta do servidor de autorizacao.",
                    ex
            );
        }
    }

    private ResponseStatusException traduzirErro(final ResponseEntity<String> response) {
        try {
            JsonNode payload = objectMapper.readTree(Objects.requireNonNullElse(response.getBody(), "{}"));
            String error = payload.path("error").asText("");
            String description = payload.path("error_description").asText("");
            if ("invalid_grant".equals(error) || response.getStatusCode().value() == 401) {
                return new ResponseStatusException(
                        UNAUTHORIZED,
                        description.isBlank() ? "Credenciais invalidas." : description
                );
            }
        } catch (JsonProcessingException ignored) {
            // cai no erro generico abaixo
        }
        return new ResponseStatusException(
                BAD_GATEWAY,
                "Nao foi possivel autenticar a sessao no servidor de autorizacao."
        );
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }

    private record ParametrosTrocaTokenSocial(String issuer, String subjectTokenType) {
    }
}
