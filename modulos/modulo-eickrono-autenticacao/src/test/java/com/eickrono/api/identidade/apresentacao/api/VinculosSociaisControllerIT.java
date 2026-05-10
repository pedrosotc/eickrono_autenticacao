package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.support.ClienteAdministracaoCadastroKeycloakStubConfiguration;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ClienteAdministracaoCadastroKeycloakStubConfiguration.class)
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class VinculosSociaisControllerIT {

    private static final String ENDPOINT = "/identidade/vinculos-sociais";
    private static final String ENDPOINT_CONTA = "/api/conta/redes-sociais";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteAdministracaoCadastroKeycloakStubConfiguration keycloakStub;

    @Autowired
    private VinculoSocialRepositorio vinculoSocialRepositorio;

    @Autowired
    private FormaAcessoRepositorio formaAcessoRepositorio;

    @MockBean
    private AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;

    @BeforeEach
    void limparEstado() {
        keycloakStub.limparIdentidadesFederadas();
        vinculoSocialRepositorio.deleteAll();
        formaAcessoRepositorio.deleteAll();
        when(autenticacaoSessaoInternaServico.autenticar("teste@eickrono.com", "SenhaAtual123"))
                .thenReturn(new SessaoInternaAutenticada(true, "Bearer", "access", "refresh", 300L));
    }

    @Test
    void deveSincronizarListarERemoverVinculosSociais() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com")));

        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(true))
                .andExpect(jsonPath("$.provedores[0].identificadorMascarado").value("t***@gmail.com"));

        assertThat(vinculoSocialRepositorio.findAll())
                .extracting(VinculoSocial::getProvedor, VinculoSocial::getIdentificador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("google", "teste@gmail.com"));
        assertThat(formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .toList())
                .extracting(FormaAcesso::getProvedor, FormaAcesso::getIdentificador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GOOGLE", "google-sub-1"));
        FormaAcesso formaSocial = formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .findFirst()
                .orElseThrow();
        formaAcessoRepositorio.save(new FormaAcesso(
                formaSocial.getPessoa(),
                TipoFormaAcesso.EMAIL_SENHA,
                "EMAIL",
                "teste@eickrono.com",
                true,
                OffsetDateTime.parse("2024-05-02T15:00:00Z"),
                OffsetDateTime.parse("2024-05-02T15:00:00Z")));

        mockMvc.perform(get(ENDPOINT)
                        .with(jwtEscopo("vinculos:ler")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(true));

        mockMvc.perform(delete(ENDPOINT + "/google")
                        .contentType("application/json")
                        .content("""
                                {"senhaConfirmacao":"SenhaAtual123"}
                                """)
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(false));

        assertThat(vinculoSocialRepositorio.findAll()).isEmpty();
        assertThat(formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .toList()).isEmpty();
    }

    @Test
    void deveExigirSenhaAtualParaDesvincularRedeSocial() throws Exception {
        prepararVinculoGoogleComSenhaLocal();

        mockMvc.perform(delete(ENDPOINT + "/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("senha_confirmacao_obrigatoria"))
                .andExpect(jsonPath("$.detalhes.provedor").value("google"))
                .andExpect(jsonPath("$.detalhes.exigeReautenticacao").value(true));
    }

    @Test
    void deveRejeitarSenhaIncorretaAoDesvincularRedeSocial() throws Exception {
        prepararVinculoGoogleComSenhaLocal();
        when(autenticacaoSessaoInternaServico.autenticar("teste@eickrono.com", "SenhaErrada"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "credenciais_invalidas"
                ));

        mockMvc.perform(delete(ENDPOINT + "/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaConfirmacao":"SenhaErrada"}
                                """)
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("senha_confirmacao_invalida"))
                .andExpect(jsonPath("$.detalhes.provedor").value("google"))
                .andExpect(jsonPath("$.detalhes.exigeReautenticacao").value(true));
    }

    @Test
    void deveNegarListagemSemEscopo() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(jwtSemEscopo()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveAceitarListagemPelaRotaCanonicaDeConta() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com")));

        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_CONTA)
                        .with(jwtEscopo("vinculos:ler")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(true));
    }

    @Test
    void deveAtualizarAvatarPreferidoSocialPorProjeto() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com",
                        "Pessoa Google",
                        "https://cdn.eickrono.test/google.png")));

        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .param("aplicacaoId", "eickrono-thimisu-app")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].urlAvatarExterno")
                        .value("https://cdn.eickrono.test/google.png"));

        mockMvc.perform(put(ENDPOINT + "/avatar-preferido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "origem": "SOCIAL",
                                  "provedor": "google"
                                }
                                """)
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarPreferidoOrigem").value("SOCIAL"))
                .andExpect(jsonPath("$.avatarPreferidoUrl").value("https://cdn.eickrono.test/google.png"))
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].avatarPrincipalNoProjeto").value(true))
                .andExpect(jsonPath("$.provedores[0].urlAvatarExterno")
                        .value("https://cdn.eickrono.test/google.png"));
    }

    @Test
    void deveSincronizarVinculoSocialSemFotoDisponivel() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com",
                        "Pessoa Google",
                        null)));

        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .param("aplicacaoId", "eickrono-thimisu-app")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].avatarPrincipalNoProjeto").value(false))
                .andExpect(jsonPath("$.provedores[0].statusAvatarSocial").value("FOTO_NAO_DISPONIVEL"))
                .andExpect(jsonPath("$.provedores[0].mensagemAvatarSocial")
                        .value("Esta conta esta vinculada, mas nao ha foto disponivel para usar no perfil neste momento."));

        assertThat(formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .map(FormaAcesso::getUrlAvatarExterno)
                .toList())
                .containsExactly((String) null);
    }

    @Test
    void deveInformarQuandoProvedorNaoSuportaFotoNoProjetoAtual() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.APPLE,
                        "apple-sub-1",
                        "usuario@icloud.test",
                        "Pessoa Apple",
                        null)));

        mockMvc.perform(post(ENDPOINT + "/apple/sincronizacao")
                        .param("aplicacaoId", "eickrono-thimisu-app")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[1].provedor").value("apple"))
                .andExpect(jsonPath("$.provedores[1].vinculado").value(true))
                .andExpect(jsonPath("$.provedores[1].statusAvatarSocial")
                        .value("PROVEDOR_SEM_SUPORTE_DE_FOTO"))
                .andExpect(jsonPath("$.provedores[1].mensagemAvatarSocial")
                        .value("Esta conta esta vinculada, mas este provedor nao disponibiliza foto para uso no perfil neste aplicativo."));
    }

    @Test
    void deveRejeitarAvatarPreferidoSocialQuandoRedeNaoPossuiFoto() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com",
                        "Pessoa Google",
                        null)));

        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .param("aplicacaoId", "eickrono-thimisu-app")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk());

        mockMvc.perform(put(ENDPOINT + "/avatar-preferido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "origem": "SOCIAL",
                                  "provedor": "google"
                                }
                                """)
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("avatar_social_indisponivel"))
                .andExpect(jsonPath("$.detalhes.provedor").value("google"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtEscopo(final String escopo) {
        return jwt().jwt(builder -> builder
                        .subject("sub-123")
                        .claim("email", "teste@eickrono.com")
                        .claim("name", "Pessoa Teste"))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + Objects.requireNonNull(escopo)));
    }

    private void prepararVinculoGoogleComSenhaLocal() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com")));
        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk());
        FormaAcesso formaSocial = formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .findFirst()
                .orElseThrow();
        formaAcessoRepositorio.save(new FormaAcesso(
                formaSocial.getPessoa(),
                TipoFormaAcesso.EMAIL_SENHA,
                "EMAIL",
                "teste@eickrono.com",
                true,
                OffsetDateTime.parse("2024-05-02T15:00:00Z"),
                OffsetDateTime.parse("2024-05-02T15:00:00Z")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtSemEscopo() {
        return jwt().jwt(builder -> builder
                .subject("sub-123")
                .claim("email", "teste@eickrono.com")
                .claim("name", "Pessoa Teste"));
    }
}
