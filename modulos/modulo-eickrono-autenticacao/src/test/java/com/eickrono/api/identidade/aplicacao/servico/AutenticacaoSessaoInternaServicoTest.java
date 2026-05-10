package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.infraestrutura.configuracao.SessaoInternaKeycloakProperties;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class AutenticacaoSessaoInternaServicoTest {

    private static final String URL_TOKEN = "http://localhost:8080/realms/eickrono/protocol/openid-connect/token";
    private static final String PASSWORD_PEPPER = "pepperLocalDevTrocar";

    private AutenticacaoSessaoInternaServico servico;
    private MockRestServiceServer mockServer;
    private ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;

    @BeforeEach
    void setUp() {
        SessaoInternaKeycloakProperties properties = new SessaoInternaKeycloakProperties();
        properties.setUrlBase("http://localhost:8080");
        properties.setRealm("eickrono");
        properties.setClientId("app-flutter-local");
        properties.setClientSecret("");
        properties.setPasswordPepper(PASSWORD_PEPPER);

        clienteAdministracaoCadastroKeycloak = Mockito.mock(ClienteAdministracaoCadastroKeycloak.class);
        when(clienteAdministracaoCadastroKeycloak.buscarUsuarioPorEmail("ana@eickrono.com"))
                .thenReturn(Optional.of(new UsuarioCadastroKeycloakExistente(
                        "sub-ana",
                        "ana@eickrono.com",
                        true,
                        true,
                        1774513396823L
                )));
        when(clienteAdministracaoCadastroKeycloak.buscarUsuarioPorEmail("desconhecido@eickrono.com"))
                .thenReturn(Optional.empty());

        servico = new AutenticacaoSessaoInternaServico(
                new RestTemplateBuilder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                clienteAdministracaoCadastroKeycloak
        );
        RestTemplate restTemplate = Objects.requireNonNull(
                (RestTemplate) ReflectionTestUtils.getField(servico, "restTemplate"));
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("deve autenticar sessao interna no token endpoint do Keycloak")
    void deveAutenticarSessaoInterna() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=password")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=app-flutter-local")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("username=ana%40eickrono.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "password=SenhaForte123" + PASSWORD_PEPPER + "1774513396823")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-123",
                          "refresh_token": "refresh-token-456",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessaoInternaAutenticada sessao = servico.autenticar("ana@eickrono.com", "SenhaForte123");

        assertThat(sessao.autenticado()).isTrue();
        assertThat(sessao.tipoToken()).isEqualTo("Bearer");
        assertThat(sessao.accessToken()).isEqualTo("access-token-123");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-token-456");
        assertThat(sessao.expiresIn()).isEqualTo(3600L);
        mockServer.verify();
    }

    @Test
    @DisplayName("deve usar a senha crua quando o usuário não existe no Keycloak")
    void deveManterSenhaCruaQuandoUsuarioNaoExiste() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("username=desconhecido%40eickrono.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password=SenhaForte123")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-123",
                          "refresh_token": "refresh-token-456",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessaoInternaAutenticada sessao = servico.autenticar("desconhecido@eickrono.com", "SenhaForte123");

        assertThat(sessao.autenticado()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve mapear invalid_grant para credenciais invalidas")
    void deveMapearCredenciaisInvalidas() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": "invalid_grant",
                                  "error_description": "Invalid user credentials"
                                }
                                """));

        assertThatThrownBy(() -> servico.autenticar("ana@eickrono.com", "senha-errada"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException response = (ResponseStatusException) ex;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("deve renovar sessao interna pelo token endpoint do Keycloak")
    void deveRenovarSessaoInterna() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=app-flutter-local")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=refresh-token-antigo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("device_token=device-token-123")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-novo",
                          "refresh_token": "refresh-token-novo",
                          "expires_in": 1800,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessaoInternaAutenticada sessao = servico.renovar("refresh-token-antigo", "device-token-123");

        assertThat(sessao.autenticado()).isTrue();
        assertThat(sessao.tipoToken()).isEqualTo("Bearer");
        assertThat(sessao.accessToken()).isEqualTo("access-token-novo");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-token-novo");
        assertThat(sessao.expiresIn()).isEqualTo(1800L);
        mockServer.verify();
    }

    @Test
    @DisplayName("deve autenticar sessao social com token exchange do Google")
    void deveAutenticarSessaoSocialGoogle() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=app-flutter-local")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("requested_token_type="
                        + "urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Arefresh_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject_issuer=google")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject_token_type="
                        + "urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aaccess_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject_token=google-token-123")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-social",
                          "refresh_token": "refresh-token-social",
                          "expires_in": 900,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessaoInternaAutenticada sessao = servico.autenticarSocial("google", "google-token-123");

        assertThat(sessao.autenticado()).isTrue();
        assertThat(sessao.accessToken()).isEqualTo("access-token-social");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-token-social");
        assertThat(sessao.expiresIn()).isEqualTo(900L);
        mockServer.verify();
    }

    @Test
    @DisplayName("deve autenticar sessao social com id token da Apple")
    void deveAutenticarSessaoSocialApple() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject_issuer=apple")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject_token_type="
                        + "urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject_token=apple-token-456")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-apple",
                          "refresh_token": "refresh-token-apple",
                          "expires_in": 900,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessaoInternaAutenticada sessao = servico.autenticarSocial("apple", "apple-token-456");

        assertThat(sessao.autenticado()).isTrue();
        assertThat(sessao.accessToken()).isEqualTo("access-token-apple");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-token-apple");
        mockServer.verify();
    }
}
