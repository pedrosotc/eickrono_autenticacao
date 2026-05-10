package com.eickrono.api.identidade.infraestrutura.integracao;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.aplicacao.modelo.CadastroKeycloakProvisionado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoCadastroKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.DerivacaoSenhaKeycloak;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroInternoKeycloakProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@Profile("!test")
public class ClienteAdministracaoCadastroKeycloakHttp
        implements ClienteAdministracaoCadastroKeycloak, ClienteAdministracaoVinculosSociaisKeycloak {

    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();
    private static final String ATRIBUTO_DATA_NASCIMENTO = "data_nascimento";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CadastroInternoKeycloakProperties properties;

    public ClienteAdministracaoCadastroKeycloakHttp(final RestTemplateBuilder restTemplateBuilder,
                                                    final ObjectMapper objectMapper,
                                                    final CadastroInternoKeycloakProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(this.properties.getTimeout())
                .setReadTimeout(this.properties.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper é obrigatório");
    }

    @Override
    public CadastroKeycloakProvisionado criarUsuarioPendente(final String nomeCompleto,
                                                             final String emailPrincipal,
                                                             final String senhaPura) {
        String accessToken = obterAccessTokenAdministrador();
        DadosNomeUsuario dadosNome = DadosNomeUsuario.de(nomeCompleto);
        ObjectNode requisicao = objectMapper.createObjectNode();
        requisicao.put("username", emailPrincipal);
        requisicao.put("email", emailPrincipal);
        requisicao.put("firstName", dadosNome.primeiroNome());
        requisicao.put("lastName", dadosNome.sobrenome());
        requisicao.put("enabled", false);
        requisicao.put("emailVerified", false);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                URI.create(urlUsuarios()),
                HttpMethod.POST,
                new HttpEntity<>(requisicao, cabecalhosJson(accessToken)),
                String.class
        );
        if (createResponse.getStatusCode().value() == CONFLICT.value()) {
            throw new ResponseStatusException(CONFLICT, "Já existe um usuário de autenticação com o e-mail informado.");
        }
        if (!createResponse.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível criar o usuário pendente no servidor de autorização.");
        }

        String userId = extrairUserId(createResponse);
        JsonNode usuarioCriado = consultarUsuario(accessToken, userId);
        long createdTimestamp = usuarioCriado.path("createdTimestamp").asLong(0L);
        if (createdTimestamp <= 0L) {
            throw erroGenerico("O servidor de autorização não retornou createdTimestamp para o novo usuário.");
        }

        resetarSenha(
                accessToken,
                userId,
                DerivacaoSenhaKeycloak.derivar(senhaPura, createdTimestamp, properties.getPasswordPepper())
        );

        ObjectNode atualizacao = usuarioCriado.deepCopy();
        atualizacao.put("enabled", false);
        atualizacao.put("emailVerified", false);
        atualizarUsuario(accessToken, userId, atualizacao);

        return new CadastroKeycloakProvisionado(userId, emailPrincipal, nomeCompleto);
    }

    @Override
    public void confirmarEmailEAtivarUsuario(final String subjectRemoto,
                                             final String nomeCompleto,
                                             final LocalDate dataNascimento) {
        String accessToken = obterAccessTokenAdministrador();
        JsonNode usuario = consultarUsuario(accessToken, subjectRemoto);
        ObjectNode atualizacao = usuario.deepCopy();
        DadosNomeUsuario dadosNome = DadosNomeUsuario.de(nomeCompleto);
        atualizacao.put("enabled", true);
        atualizacao.put("emailVerified", true);
        atualizacao.put("firstName", dadosNome.primeiroNome());
        atualizacao.put("lastName", dadosNome.sobrenome());
        if (dataNascimento != null) {
            ObjectNode atributos = garantirObjeto(atualizacao, "attributes");
            atributos.set(ATRIBUTO_DATA_NASCIMENTO, arrayComValor(dataNascimento.toString()));
        }
        atualizarUsuario(accessToken, subjectRemoto, atualizacao);
    }

    @Override
    public void removerUsuarioPendente(final String subjectRemoto) {
        String accessToken = obterAccessTokenAdministrador();
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório")),
                HttpMethod.DELETE,
                new HttpEntity<>(cabecalhosJson(accessToken)),
                String.class
        );
        if (response.getStatusCode().value() == 404) {
            return;
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível remover o usuário pendente no servidor de autorização.");
        }
    }

    @Override
    public Optional<UsuarioCadastroKeycloakExistente> buscarUsuarioPorEmail(final String emailPrincipal) {
        String emailNormalizado = Objects.requireNonNull(emailPrincipal, "emailPrincipal é obrigatório").trim();
        if (emailNormalizado.isBlank()) {
            return Optional.empty();
        }
        String accessToken = obterAccessTokenAdministrador();
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "?email=" + UriEscapers.escapeQueryParam(emailNormalizado) + "&exact=true"),
                HttpMethod.GET,
                new HttpEntity<>(cabecalhosJson(accessToken)),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível consultar o usuário por e-mail no servidor de autorização.");
        }
        try {
            JsonNode lista = objectMapper.readTree(Objects.requireNonNullElse(response.getBody(), "[]"));
            if (!lista.isArray() || lista.isEmpty()) {
                return Optional.empty();
            }
            JsonNode usuario = lista.get(0);
            return Optional.of(new UsuarioCadastroKeycloakExistente(
                    usuario.path("id").asText(),
                    usuario.path("email").asText(emailNormalizado),
                    usuario.path("emailVerified").asBoolean(false),
                    usuario.path("enabled").asBoolean(false),
                    usuario.path("createdTimestamp").asLong(0L)
            ));
        } catch (JsonProcessingException ex) {
            throw erroGenerico("Falha ao interpretar o usuário consultado por e-mail.", ex);
        }
    }

    @Override
    public void vincularIdentidadeFederada(final String subjectRemoto,
                                           final IdentidadeFederadaKeycloak identidadeFederada) {
        String accessToken = obterAccessTokenAdministrador();
        String userId = Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório");
        IdentidadeFederadaKeycloak identidade = Objects.requireNonNull(
                identidadeFederada, "identidadeFederada é obrigatória");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("identityProvider", identidade.provedor().getAliasApi());
        payload.put("userId", identidade.identificadorCanonico());
        if (textoOuNulo(identidade.nomeUsuarioExterno()) != null) {
            payload.put("userName", identidade.nomeUsuarioExterno());
        }

        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + userId + "/federated-identity/" + identidade.provedor().getAliasApi()),
                HttpMethod.POST,
                new HttpEntity<>(payload, cabecalhosJson(accessToken)),
                String.class
        );
        if (response.getStatusCode().value() == CONFLICT.value() && identidadeFederadaJaAssociada(userId, identidade)) {
            return;
        }
        if (response.getStatusCode().value() == 204) {
            return;
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível vincular a identidade federada ao usuário.");
        }
    }

    @Override
    public void redefinirSenha(final String subjectRemoto, final String senhaPura) {
        String accessToken = obterAccessTokenAdministrador();
        JsonNode usuario = consultarUsuario(accessToken, Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
        long createdTimestamp = usuario.path("createdTimestamp").asLong(0L);
        if (createdTimestamp <= 0L) {
            throw erroGenerico("O usuário de autenticação não retornou createdTimestamp para redefinição de senha.");
        }
        resetarSenha(
                accessToken,
                subjectRemoto,
                DerivacaoSenhaKeycloak.derivar(senhaPura, createdTimestamp, properties.getPasswordPepper())
        );
    }

    @Override
    public List<IdentidadeFederadaKeycloak> listarIdentidadesFederadas(final String subjectRemoto) {
        String accessToken = obterAccessTokenAdministrador();
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório")
                        + "/federated-identity"),
                HttpMethod.GET,
                new HttpEntity<>(cabecalhosJson(accessToken)),
                String.class
        );
        if (response.getStatusCode().value() == 404) {
            throw erroGenerico("Usuário autenticado não encontrado no Keycloak para consulta de vínculos sociais.");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível consultar as identidades federadas do usuário.");
        }
        try {
            JsonNode lista = objectMapper.readTree(Objects.requireNonNullElse(response.getBody(), "[]"));
            if (!lista.isArray()) {
                throw erroGenerico("O Keycloak retornou identidades federadas em formato inválido.");
            }
            List<IdentidadeFederadaKeycloak> identidades = new ArrayList<>();
            for (JsonNode item : lista) {
                ProvedorVinculoSocial.fromAlias(item.path("identityProvider").asText(null))
                        .ifPresent(provedor -> identidades.add(new IdentidadeFederadaKeycloak(
                                provedor,
                                textoOuNulo(item.path("userId").asText(null)),
                                textoOuNulo(item.path("userName").asText(null)))));
            }
            return identidades;
        } catch (JsonProcessingException ex) {
            throw erroGenerico("Falha ao interpretar as identidades federadas retornadas pelo Keycloak.", ex);
        }
    }

    @Override
    public void removerIdentidadeFederada(final String subjectRemoto, final ProvedorVinculoSocial provedor) {
        String accessToken = obterAccessTokenAdministrador();
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório")
                        + "/federated-identity/" + Objects.requireNonNull(provedor, "provedor é obrigatório").getAliasApi()),
                HttpMethod.DELETE,
                new HttpEntity<>(cabecalhosJson(accessToken)),
                String.class
        );
        if (response.getStatusCode().value() == 404 || response.getStatusCode().value() == 204) {
            return;
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível remover a identidade federada do usuário.");
        }
    }

    private String obterAccessTokenAdministrador() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getClientId());
        if (StringUtils.hasText(properties.getClientSecret())) {
            form.add("client_secret", properties.getClientSecret());
        }
        form.add("username", properties.getAdminUsername());
        form.add("password", properties.getAdminPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlTokenAdministrador()),
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível autenticar o cliente administrador do Keycloak.");
        }
        try {
            return objectMapper.readTree(Objects.requireNonNullElse(response.getBody(), "{}"))
                    .path("access_token")
                    .asText(null);
        } catch (JsonProcessingException ex) {
            throw erroGenerico("Falha ao interpretar o token administrativo do Keycloak.", ex);
        }
    }

    private JsonNode consultarUsuario(final String accessToken, final String userId) {
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + userId),
                HttpMethod.GET,
                new HttpEntity<>(cabecalhosJson(accessToken)),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível consultar o usuário pendente no servidor de autorização.");
        }
        try {
            return objectMapper.readTree(Objects.requireNonNullElse(response.getBody(), "{}"));
        } catch (JsonProcessingException ex) {
            throw erroGenerico("Falha ao interpretar o usuário retornado pelo Keycloak.", ex);
        }
    }

    private void resetarSenha(final String accessToken, final String userId, final String senhaDerivada) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "password");
        payload.put("temporary", false);
        payload.put("value", senhaDerivada);
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + userId + "/reset-password"),
                HttpMethod.PUT,
                new HttpEntity<>(payload, cabecalhosJson(accessToken)),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível definir a senha inicial do usuário pendente.");
        }
    }

    private void atualizarUsuario(final String accessToken, final String userId, final JsonNode payload) {
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(urlUsuarios() + "/" + userId),
                HttpMethod.PUT,
                new HttpEntity<>(payload, cabecalhosJson(accessToken)),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw erroGenerico("Não foi possível atualizar o usuário no servidor de autorização.");
        }
    }

    private HttpHeaders cabecalhosJson(final String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(Objects.requireNonNull(accessToken, "accessToken é obrigatório"));
        return headers;
    }

    private ObjectNode garantirObjeto(final ObjectNode noPai, final String nomeCampo) {
        JsonNode atual = noPai.get(nomeCampo);
        if (atual instanceof ObjectNode objeto) {
            return objeto;
        }
        ObjectNode criado = objectMapper.createObjectNode();
        noPai.set(nomeCampo, criado);
        return criado;
    }

    private ArrayNode arrayComValor(final String valor) {
        ArrayNode valores = objectMapper.createArrayNode();
        valores.add(valor);
        return valores;
    }

    private String extrairUserId(final ResponseEntity<String> response) {
        List<String> locations = response.getHeaders().get(HttpHeaders.LOCATION);
        if (locations != null && !locations.isEmpty()) {
            String location = locations.getFirst();
            int ultimoSeparador = location.lastIndexOf('/');
            if (ultimoSeparador >= 0 && ultimoSeparador < location.length() - 1) {
                return location.substring(ultimoSeparador + 1);
            }
        }
        throw erroGenerico("O Keycloak não retornou o identificador do usuário recém-criado.");
    }

    private String urlTokenAdministrador() {
        return properties.getUrlBase()
                + "/realms/" + properties.getAdminRealm()
                + "/protocol/openid-connect/token";
    }

    private String urlUsuarios() {
        return properties.getUrlBase() + "/admin/realms/" + properties.getRealm() + "/users";
    }

    private String textoOuNulo(final String valor) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        return valor.trim();
    }

    private boolean identidadeFederadaJaAssociada(final String subjectRemoto,
                                                  final IdentidadeFederadaKeycloak identidade) {
        return listarIdentidadesFederadas(subjectRemoto).stream().anyMatch(atual ->
                atual.provedor() == identidade.provedor()
                        && Objects.equals(atual.identificadorExterno(), identidade.identificadorExterno())
                        && Objects.equals(textoOuNulo(atual.nomeUsuarioExterno()), textoOuNulo(identidade.nomeUsuarioExterno())));
    }

    private ResponseStatusException erroGenerico(final String mensagem) {
        return new ResponseStatusException(BAD_GATEWAY, mensagem);
    }

    private ResponseStatusException erroGenerico(final String mensagem, final Exception ex) {
        return new ResponseStatusException(BAD_GATEWAY, mensagem, ex);
    }

    private record DadosNomeUsuario(String primeiroNome, String sobrenome) {
        private static DadosNomeUsuario de(final String nomeCompleto) {
            String nomeNormalizado = Objects.requireNonNull(nomeCompleto, "nomeCompleto é obrigatório").trim();
            if (nomeNormalizado.isBlank()) {
                throw new IllegalArgumentException("nomeCompleto é obrigatório");
            }
            int indiceSeparador = nomeNormalizado.indexOf(' ');
            if (indiceSeparador < 0) {
                return new DadosNomeUsuario(nomeNormalizado, nomeNormalizado);
            }
            String primeiroNome = nomeNormalizado.substring(0, indiceSeparador).trim();
            String sobrenome = nomeNormalizado.substring(indiceSeparador + 1).trim();
            if (sobrenome.isBlank()) {
                sobrenome = primeiroNome;
            }
            return new DadosNomeUsuario(primeiroNome, sobrenome);
        }
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }

    private static final class UriEscapers {
        private UriEscapers() {
        }

        private static String escapeQueryParam(final String value) {
            return value.replace(" ", "%20");
        }
    }
}
