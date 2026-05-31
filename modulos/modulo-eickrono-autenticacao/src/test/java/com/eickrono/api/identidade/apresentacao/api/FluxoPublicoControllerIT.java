package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.aplicacao.servico.AvaliacaoSegurancaAplicativoService;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaPendenteScheduler;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoCadastroKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.servico.IntegracaoProdutoPendenteScheduler;
import com.eickrono.api.identidade.aplicacao.servico.LocalizadorLoginSocialProjetoJdbc;
import com.eickrono.api.identidade.aplicacao.servico.LocalizadorPerfilSistemaProjetoPorEmailJdbc;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorContextoAutenticacaoService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorProjetoFluxoPublico;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoScheduler;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.servico.ValidadorCredencialSocialNativaService;
import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.CredencialSocialDeclarada;
import com.eickrono.api.identidade.aplicacao.modelo.CredencialSocialValidada;
import com.eickrono.api.identidade.aplicacao.modelo.LoginSocialProjetoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.PerfilSistemaProjetoPorEmailResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = FluxoPublicoControllerIT.LocalDatabaseOidcInitializer.class)
class FluxoPublicoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CadastroContaInternaServico cadastroContaInternaServico;

    @MockBean
    private AtestacaoAppServico atestacaoAppServico;

    @MockBean
    private AvaliacaoSegurancaAplicativoService avaliacaoSegurancaAplicativoService;

    @MockBean
    private AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;

    @MockBean
    private ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;

    @MockBean
    private ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService;

    @MockBean
    private ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;

    @MockBean
    private ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;

    @MockBean
    private LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail;

    @MockBean
    private RegistroDispositivoService registroDispositivoService;

    @MockBean
    private RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;

    @MockBean
    private LocalizadorLoginSocialProjetoJdbc localizadorLoginSocialProjeto;

    @MockBean
    private ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;

    @MockBean
    private ValidadorCredencialSocialNativaService validadorCredencialSocialNativaService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CadastroContaPendenteScheduler cadastroContaPendenteScheduler;

    @MockBean
    private RegistroDispositivoScheduler registroDispositivoScheduler;

    @MockBean
    private IntegracaoProdutoPendenteScheduler integracaoProdutoPendenteScheduler;

    @Test
    void deveCarregarMocksObrigatoriosDoContextoDeFluxoPublico() {
        assertThat(clienteContextoPessoaPerfilSistema).isNotNull();
        assertThat(clienteAdministracaoVinculosSociaisKeycloak).isNotNull();
    }

    static final class LocalDatabaseOidcInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final String DEFAULT_DB_HOST = "localhost";
        private static final String DEFAULT_DB_PORT = "5432";
        private static final String DEFAULT_DB_NAME = "eickrono_identidade";
        private static final String DEFAULT_DB_USER = "eickrono";
        private static final String DEFAULT_DB_PASSWORD = "senhaLocalDev";

        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            String issuer = InfraestruturaTesteIdentidade.obterIssuer();
            TestPropertyValues.of(
                    "spring.datasource.url=" + jdbcUrl(),
                    "spring.datasource.username=" + env("EICKRONO_TEST_DB_USER", DEFAULT_DB_USER),
                    "spring.datasource.password=" + env("EICKRONO_TEST_DB_PASSWORD", DEFAULT_DB_PASSWORD),
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.flyway.enabled=false",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
                            + issuer + "/protocol/openid-connect/certs",
                    "fapi.seguranca.audiencia-esperada=eickrono-autenticacao"
            ).applyTo(context.getEnvironment());
            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event ->
                    InfraestruturaTesteIdentidade.encerrarInfraestrutura());
        }

        private static String jdbcUrl() {
            String explicit = System.getenv("EICKRONO_TEST_JDBC_URL");
            if (explicit != null && !explicit.isBlank()) {
                return explicit.trim();
            }
            return "jdbc:postgresql://"
                    + env("EICKRONO_TEST_DB_HOST", DEFAULT_DB_HOST)
                    + ":"
                    + env("EICKRONO_TEST_DB_PORT", DEFAULT_DB_PORT)
                    + "/"
                    + env("EICKRONO_TEST_DB_NAME", DEFAULT_DB_NAME);
        }

        private static String env(final String name, final String fallback) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(
                clienteAdministracaoCadastroKeycloak,
                cadastroContaPendenteScheduler,
                registroDispositivoScheduler,
                integracaoProdutoPendenteScheduler
        );
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app"))
                .thenReturn(new ProjetoFluxoPublicoResolvido(
                        1L,
                        "eickrono-thimisu-app",
                        "Thimisu",
                        "APP",
                        "Thimisu",
                        "MOBILE",
                        false
                ));
        when(atestacaoAppServico.validarComprovante(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida(
                        null,
                        com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado.naoExecutada(
                                "validacao oficial nao executada no teste"
                        ),
                        com.eickrono.api.identidade.aplicacao.modelo.StatusValidacaoAtestacaoApp.VALIDADA_LOCALMENTE
                ));
        org.mockito.Mockito.doAnswer(invocacao -> new com.eickrono.api.identidade.aplicacao.modelo
                        .AvaliacaoSegurancaAplicativoRealizada(false, true, 0, java.util.List.of()))
                .when(avaliacaoSegurancaAplicativoService)
                .avaliar(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );
        when(registroDispositivoLoginSilenciosoService.registrar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DispositivoSessaoRegistrado(
                        "device-token-teste",
                        OffsetDateTime.parse("2026-03-27T20:00:00Z")
                ));
    }

    @Test
    void deveConsultarDisponibilidadePublicaDoUsuario() throws Exception {
        when(cadastroContaInternaServico.identificadorPublicoSistemaDisponivelPublico("ana.souza")).thenReturn(false);

        mockMvc.perform(get("/api/publica/cadastros/usuarios/disponibilidade")
                        .param("usuario", " Ana.Souza "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana.souza"))
                .andExpect(jsonPath("$.disponivel").value(false));

        verify(cadastroContaInternaServico).identificadorPublicoSistemaDisponivelPublico("ana.souza");
    }

    @Test
    void deveConsultarDisponibilidadePublicaDoUsuarioPorAplicacao() throws Exception {
        when(cadastroContaInternaServico.identificadorPublicoSistemaDisponivelPublico(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);

        mockMvc.perform(get("/api/publica/cadastros/usuarios/disponibilidade")
                        .param("usuario", " Ana.Souza ")
                        .param("aplicacaoId", "eickrono-thimisu-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana.souza"))
                .andExpect(jsonPath("$.disponivel").value(true));

        verify(cadastroContaInternaServico)
                .identificadorPublicoSistemaDisponivelPublico("ana.souza", "eickrono-thimisu-app");
    }

    @Test
    void deveCriarCadastroPublicoComAplicacaoIdComoSistemaSolicitante() throws Exception {
        UUID cadastroId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(cadastroContaInternaServico.cadastrarPublico(
                eq(com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro.FISICA),
                eq("Ana Souza"),
                eq("Ana LTDA"),
                eq("ana.souza"),
                eq(com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro.FEMININO),
                eq("BR"),
                eq(java.time.LocalDate.parse("1994-08-17")),
                eq("ana@eickrono.com"),
                eq("+5511999999999"),
                eq(com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro.SMS),
                eq("SenhaForte123"),
                eq("eickrono-thimisu-app"),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado(
                        cadastroId,
                        "sub-ana",
                        "ana@eickrono.com",
                        true
                ));

        mockMvc.perform(post("/api/publica/cadastros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "tipoPessoa": "FISICA",
                                  "nomeCompleto": "Ana Souza",
                                  "nomeFantasia": "Ana LTDA",
                                  "usuario": "ana.souza",
                                  "sexo": "FEMININO",
                                  "paisNascimento": "BR",
                                  "dataNascimento": "1994-08-17",
                                  "emailPrincipal": "ana@eickrono.com",
                                  "telefone": "+5511999999999",
                                  "tipoValidacaoTelefone": "SMS",
                                  "senha": "SenhaForte123",
                                  "confirmacaoSenha": "SenhaForte123",
                                  "aceitouTermos": true,
                                  "aceitouPrivacidade": true,
                                  "plataformaApp": "IOS",
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cadastroId").value(cadastroId.toString()))
                .andExpect(jsonPath("$.statusUsuario").value("PENDENTE_EMAIL"));

        verify(cadastroContaInternaServico).cadastrarPublico(
                eq(com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro.FISICA),
                eq("Ana Souza"),
                eq("Ana LTDA"),
                eq("ana.souza"),
                eq(com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro.FEMININO),
                eq("BR"),
                eq(java.time.LocalDate.parse("1994-08-17")),
                eq("ana@eickrono.com"),
                eq("+5511999999999"),
                eq(com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro.SMS),
                eq("SenhaForte123"),
                eq("eickrono-thimisu-app"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void deveCancelarCadastroPendentePublico() throws Exception {
        mockMvc.perform(delete("/api/publica/cadastros/{cadastroId}",
                        "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNoContent());

        verify(cadastroContaInternaServico).cancelarCadastroPendentePublico(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void deveMapearContaNaoLiberadaQuandoKeycloakRetornaContaDesabilitada() throws Exception {
        UUID cadastroId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(autenticacaoSessaoInternaServico.autenticar("b@b.com", "SenhaForte123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account disabled"));
        when(cadastroContaInternaServico.buscarCadastroPendenteEmailPublico("b@b.com"))
                .thenReturn(Optional.of(cadastroId));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "b@b.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_nao_liberada"))
                .andExpect(jsonPath("$.detalhes.cadastroId").value(cadastroId.toString()));
    }

    @Test
    void deveRetornarRegistroInterativoPendenteNoLoginPorSenhaQuandoDispositivoNaoEstiverLiberado() throws Exception {
        UUID registroId = UUID.fromString("44444444-5555-6666-7777-888888888888");
        OffsetDateTime expiraEm = OffsetDateTime.parse("2026-03-28T10:15:30Z");
        when(autenticacaoSessaoInternaServico.autenticar("senha@eickrono.com", "SenhaForte123"))
                .thenReturn(new SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token-senha",
                        "refresh-token-senha",
                        1800
                ));
        when(resolvedorContextoAutenticacaoService.buscarPorEmailPublico("senha@eickrono.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        77L,
                        "usuario-senha",
                        "senha@eickrono.com",
                        "Usuario Senha",
                        null,
                        "LIBERADO"
                )));
        when(registroDispositivoLoginSilenciosoService.registrar(any(ContextoPessoaPerfilSistema.class), any()))
                .thenThrow(new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "dispositivo_nao_liberado",
                        "Dispositivo pendente de confirmacao."
                ));
        when(registroDispositivoService.solicitarRegistroParaSessao(any(ContextoPessoaPerfilSistema.class), any()))
                .thenReturn(new RegistroDispositivoResponse(
                        registroId,
                        expiraEm,
                        StatusRegistroDispositivo.PENDENTE,
                        java.util.List.of(CanalVerificacao.EMAIL)
                ));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLoginPublico("""
                                "login": "senha@eickrono.com",
                                "senha": "SenhaForte123",
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token-senha"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-senha"))
                .andExpect(jsonPath("$.tokenDispositivo").isEmpty())
                .andExpect(jsonPath("$.registroDispositivoId").value(registroId.toString()))
                .andExpect(jsonPath("$.registroDispositivoExpiraEm").value("2026-03-28T10:15:30Z"))
                .andExpect(jsonPath("$.statusRegistroDispositivo").value("PENDENTE"))
                .andExpect(jsonPath("$.canaisConfirmacao[0]").value("EMAIL"))
                .andExpect(jsonPath("$.statusUsuario").value("LIBERADO"))
                .andExpect(jsonPath("$.emailPrincipal").value("senha@eickrono.com"))
                .andExpect(jsonPath("$.podeOferecerBiometria").value(false))
                .andExpect(jsonPath("$.podeOferecerVinculacaoSocial").value(false));
    }

    private static String corpoLoginPublico(final String cabecalhoVariavel) {
        return """
                {
                  "aplicacaoId": "eickrono-thimisu-app",
                  %s
                  "dispositivo": {
                    "plataforma": "IOS",
                    "identificadorInstalacao": "instalacao-teste",
                    "modelo": "simulador",
                    "sistemaOperacional": "ios",
                    "versaoSistema": "18",
                    "versaoApp": "1.0.0"
                  },
                  "atestacao": {
                    "plataforma": "IOS",
                    "provedor": "APPLE_APP_ATTEST",
                    "tipoComprovante": "OBJETO_ASSERCAO",
                    "identificadorDesafio": "desafio",
                    "desafioBase64": "ZGVzYWZpbw==",
                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                    "geradoEm": "2026-03-26T20:00:00Z",
                    "chaveId": "chave"
                  },
                  "segurancaAplicativo": {
                    "plataforma": "IOS",
                    "provedorAtestacao": "APPLE_APP_ATTEST",
                    "rootOuJailbreak": false,
                    "debuggerDetectado": false,
                    "hookingSuspeito": false,
                    "tamperSuspeito": false,
                    "riscoCapturaTela": false,
                    "assinaturaValida": true,
                    "identidadeAplicativoValida": true,
                    "sinaisRisco": [],
                    "scoreRiscoLocal": 0,
                    "bundleIdentifier": "com.eickrono.thimisu",
                    "teamIdentifier": "TEAM123"
                  }
                }
                """.formatted(cabecalhoVariavel);
    }

    @Test
    void deveMapearCredenciaisInvalidasQuandoKeycloakRetornaSenhaInvalida() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("a@a.com", "SenhaErrada123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user credentials"));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "a@a.com",
                                  "senha": "SenhaErrada123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("credenciais_invalidas"));
    }

    @Test
    void deveMapearContaNaoLiberadaQuandoKeycloakRetornaContaNaoConfigurada() throws Exception {
        UUID cadastroId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(autenticacaoSessaoInternaServico.autenticar("b@b.com", "SenhaForte123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not fully set up"));
        when(cadastroContaInternaServico.buscarCadastroPendenteEmailPublico("b@b.com"))
                .thenReturn(Optional.of(cadastroId));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "b@b.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_nao_liberada"))
                .andExpect(jsonPath("$.detalhes.cadastroId").value(cadastroId.toString()));
    }

    @Test
    void deveMapearContaIncompletaQuandoKeycloakRetornaContaNaoConfiguradaSemCadastroPendente() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("c@c.com", "SenhaForte123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not fully set up"));
        when(cadastroContaInternaServico.buscarCadastroPendenteEmailPublico("c@c.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "c@c.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_incompleta"));
    }

    @Test
    void deveEmitirTokenDispositivoJaNoLoginPublico() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("a@a.com", "SenhaForte123"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token",
                        "refresh-token",
                        3600
                ));
        when(resolvedorContextoAutenticacaoService.buscarPorEmailPublico("a@a.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        10L,
                        "sub-123",
                        "a@a.com",
                        "Ana",
                        "usuario-1",
                        "LIBERADO"
                )));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "a@a.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("cenario 15: deve permitir login central quando o contexto do produto estiver indisponivel")
    void devePermitirLoginCentralQuandoContextoDoProdutoEstiverIndisponivel() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("ana@eickrono.com", "SenhaForte123"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token",
                        "refresh-token",
                        3600
                ));
        when(resolvedorContextoAutenticacaoService.buscarPorEmailPublico("ana@eickrono.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        77L,
                        "sub-ana",
                        "ana@eickrono.com",
                        "Ana Souza",
                        null,
                        "PENDENTE_LIBERACAO_PRODUTO"
                )));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "ana@eickrono.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.statusUsuario").value("PENDENTE_LIBERACAO_PRODUTO"))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));

        verify(registroDispositivoLoginSilenciosoService).registrar(
                any(ContextoPessoaPerfilSistema.class),
                any()
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("cenario 15: deve permitir login central quando o perfil do produto ainda estiver pendente")
    void devePermitirLoginCentralQuandoPerfilDoProdutoEstiverPendente() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("ana@eickrono.com", "SenhaForte123"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token",
                        "refresh-token",
                        3600
                ));
        when(resolvedorContextoAutenticacaoService.buscarPorEmailPublico("ana@eickrono.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        77L,
                        "sub-ana",
                        "ana@eickrono.com",
                        "Ana Souza",
                        null,
                        "PENDENTE_LIBERACAO_PRODUTO"
                )));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "ana@eickrono.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.statusUsuario").value("PENDENTE_LIBERACAO_PRODUTO"))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));

        verify(registroDispositivoLoginSilenciosoService).registrar(
                any(ContextoPessoaPerfilSistema.class),
                any()
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("cenario 16: deve criar sessao social publica via token exchange")
    void deveCriarSessaoSocialPublica() throws Exception {
        when(validadorCredencialSocialNativaService.validar(
                eq("google"),
                eq("google-token-externo"),
                any(CredencialSocialDeclarada.class)))
                .thenReturn(new CredencialSocialValidada(
                        "google",
                        "google-sub-123",
                        "social@eickrono.com",
                        "social.user",
                        "Usuario Social",
                        "https://cdn.eickrono.store/avatar-social.png"
                ));
        when(localizadorLoginSocialProjeto.localizar(1L, "google", "google-sub-123"))
                .thenReturn(Optional.of(new LoginSocialProjetoResolvido(
                        UUID.fromString("71717171-7171-7171-7171-717171717171"),
                        "usuario-social"
                )));
        when(autenticacaoSessaoInternaServico.autenticarSocial("google", "google-token-externo"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token-social",
                        "refresh-token-social",
                        1800
                ));
        when(jwtDecoder.decode("access-token-social")).thenReturn(Jwt.withTokenValue("access-token-social")
                .header("alg", "none")
                .subject("usuario-social")
                .claim("email", "social@eickrono.com")
                .build());
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-social"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        77L,
                        "usuario-social",
                        "social@eickrono.com",
                        "Usuario Social",
                        null,
                        "LIBERADO"
                )));

        mockMvc.perform(post("/api/publica/sessoes/sociais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "provedor": "google",
                                  "tokenExterno": "google-token-externo",
                                  "dispositivo": {
                                    "plataforma": "ANDROID",
                                    "identificadorInstalacao": "instalacao-social",
                                    "modelo": "pixel",
                                    "fabricante": "google",
                                    "sistemaOperacional": "android",
                                    "versaoSistema": "15",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "ANDROID",
                                    "provedor": "GOOGLE_PLAY_INTEGRITY",
                                    "tipoComprovante": "TOKEN_INTEGRIDADE",
                                    "identificadorDesafio": "desafio-social",
                                    "desafioBase64": "ZGVzYWZpby1zb2NpYWw=",
                                    "conteudoComprovante": "dG9rZW0=",
                                    "geradoEm": "2026-03-26T20:00:00Z"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "ANDROID",
                                    "provedorAtestacao": "GOOGLE_PLAY_INTEGRITY",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "packageName": "store.eickrono.thimisu"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token-social"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-social"))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"))
                .andExpect(jsonPath("$.statusUsuario").value("LIBERADO"))
                .andExpect(jsonPath("$.emailPrincipal").value("social@eickrono.com"))
                .andExpect(jsonPath("$.podeOferecerBiometria").value(true))
                .andExpect(jsonPath("$.podeOferecerVinculacaoSocial").value(true));

        verify(registroDispositivoLoginSilenciosoService).registrar(
                any(ContextoPessoaPerfilSistema.class),
                any()
        );
    }

    @Test
    void deveRetornarRegistroInterativoPendenteNoLoginSocialQuandoDispositivoNaoEstiverLiberado() throws Exception {
        UUID registroId = UUID.fromString("51515151-5151-5151-5151-515151515151");
        OffsetDateTime expiraEm = OffsetDateTime.parse("2026-03-28T11:15:30Z");
        when(validadorCredencialSocialNativaService.validar(
                eq("google"),
                eq("google-token-pendente"),
                any(CredencialSocialDeclarada.class)))
                .thenReturn(new CredencialSocialValidada(
                        "google",
                        "google-sub-pendente",
                        "social-pendente@eickrono.com",
                        "social.pendente",
                        "Usuario Social Pendente",
                        "https://cdn.eickrono.store/avatar-social-pendente.png"
                ));
        when(localizadorLoginSocialProjeto.localizar(1L, "google", "google-sub-pendente"))
                .thenReturn(Optional.of(new LoginSocialProjetoResolvido(
                        UUID.fromString("81818181-8181-8181-8181-818181818181"),
                        "usuario-social-pendente"
                )));
        when(autenticacaoSessaoInternaServico.autenticarSocial("google", "google-token-pendente"))
                .thenReturn(new SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token-social-pendente",
                        "refresh-token-social-pendente",
                        1800
                ));
        when(jwtDecoder.decode("access-token-social-pendente")).thenReturn(Jwt.withTokenValue("access-token-social-pendente")
                .header("alg", "none")
                .subject("usuario-social-pendente")
                .claim("email", "social-pendente@eickrono.com")
                .build());
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-social-pendente"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        91L,
                        "usuario-social-pendente",
                        "social-pendente@eickrono.com",
                        "Usuario Social Pendente",
                        null,
                        "LIBERADO"
                )));
        when(registroDispositivoLoginSilenciosoService.registrar(any(ContextoPessoaPerfilSistema.class), any()))
                .thenThrow(new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "dispositivo_nao_liberado",
                        "Dispositivo pendente de confirmacao."
                ));
        when(registroDispositivoService.solicitarRegistroParaSessao(any(ContextoPessoaPerfilSistema.class), any()))
                .thenReturn(new RegistroDispositivoResponse(
                        registroId,
                        expiraEm,
                        StatusRegistroDispositivo.PENDENTE,
                        java.util.List.of(CanalVerificacao.EMAIL)
                ));

        mockMvc.perform(post("/api/publica/sessoes/sociais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "provedor": "google",
                                  "tokenExterno": "google-token-pendente",
                                  "dispositivo": {
                                    "plataforma": "ANDROID",
                                    "identificadorInstalacao": "instalacao-social-pendente",
                                    "modelo": "pixel",
                                    "fabricante": "google",
                                    "sistemaOperacional": "android",
                                    "versaoSistema": "15",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "ANDROID",
                                    "provedor": "GOOGLE_PLAY_INTEGRITY",
                                    "tipoComprovante": "TOKEN_INTEGRIDADE",
                                    "identificadorDesafio": "desafio-social-pendente",
                                    "desafioBase64": "ZGVzYWZpby1zb2NpYWwtcGVuZGVudGU=",
                                    "conteudoComprovante": "dG9rZW0=",
                                    "geradoEm": "2026-03-26T20:00:00Z"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "ANDROID",
                                    "provedorAtestacao": "GOOGLE_PLAY_INTEGRITY",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "packageName": "store.eickrono.thimisu"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token-social-pendente"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-social-pendente"))
                .andExpect(jsonPath("$.tokenDispositivo").isEmpty())
                .andExpect(jsonPath("$.registroDispositivoId").value(registroId.toString()))
                .andExpect(jsonPath("$.registroDispositivoExpiraEm").value("2026-03-28T11:15:30Z"))
                .andExpect(jsonPath("$.statusRegistroDispositivo").value("PENDENTE"))
                .andExpect(jsonPath("$.canaisConfirmacao[0]").value("EMAIL"))
                .andExpect(jsonPath("$.statusUsuario").value("LIBERADO"))
                .andExpect(jsonPath("$.emailPrincipal").value("social-pendente@eickrono.com"))
                .andExpect(jsonPath("$.podeOferecerBiometria").value(false))
                .andExpect(jsonPath("$.podeOferecerVinculacaoSocial").value(false));
    }

    @Test
    void deveRetornarRegistroInterativoPendenteNaRenovacaoQuandoSessaoLocalNaoPuderSerRecomposta() throws Exception {
        UUID registroId = UUID.fromString("61616161-6161-6161-6161-616161616161");
        OffsetDateTime expiraEm = OffsetDateTime.parse("2026-03-28T12:15:30Z");
        when(autenticacaoSessaoInternaServico.renovar("refresh-token-bootstrap", null))
                .thenReturn(new SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token-refresh",
                        "refresh-token-refresh",
                        1800
                ));
        when(jwtDecoder.decode("access-token-refresh")).thenReturn(Jwt.withTokenValue("access-token-refresh")
                .header("alg", "none")
                .subject("usuario-refresh")
                .claim("email", "refresh@eickrono.com")
                .build());
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-refresh"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        33L,
                        "usuario-refresh",
                        "refresh@eickrono.com",
                        "Usuario Refresh",
                        null,
                        "LIBERADO"
                )));
        when(registroDispositivoLoginSilenciosoService.registrar(any(ContextoPessoaPerfilSistema.class), any()))
                .thenThrow(new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "dispositivo_nao_liberado",
                        "Dispositivo pendente de confirmacao."
                ));
        when(registroDispositivoService.solicitarRegistroParaSessao(any(ContextoPessoaPerfilSistema.class), any()))
                .thenReturn(new RegistroDispositivoResponse(
                        registroId,
                        expiraEm,
                        StatusRegistroDispositivo.PENDENTE,
                        java.util.List.of(CanalVerificacao.EMAIL)
                ));

        mockMvc.perform(post("/api/publica/sessoes/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token-bootstrap",
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "dispositivo": {
                                    "plataforma": "ANDROID",
                                    "identificadorInstalacao": "instalacao-refresh",
                                    "modelo": "pixel",
                                    "fabricante": "google",
                                    "sistemaOperacional": "android",
                                    "versaoSistema": "15",
                                    "versaoApp": "1.0.0"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token-refresh"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-refresh"))
                .andExpect(jsonPath("$.tokenDispositivo").isEmpty())
                .andExpect(jsonPath("$.registroDispositivoId").value(registroId.toString()))
                .andExpect(jsonPath("$.registroDispositivoExpiraEm").value("2026-03-28T12:15:30Z"))
                .andExpect(jsonPath("$.statusRegistroDispositivo").value("PENDENTE"))
                .andExpect(jsonPath("$.canaisConfirmacao[0]").value("EMAIL"))
                .andExpect(jsonPath("$.statusUsuario").value("LIBERADO"))
                .andExpect(jsonPath("$.emailPrincipal").value("refresh@eickrono.com"))
                .andExpect(jsonPath("$.podeOferecerBiometria").value(false))
                .andExpect(jsonPath("$.podeOferecerVinculacaoSocial").value(false));
    }

    @Test
    void deveRetornarSocialSemContaLocalEntrarEVincularNoLoginSocialPublico() throws Exception {
        when(validadorCredencialSocialNativaService.validar(
                eq("google"),
                eq("google-token-externo"),
                any(CredencialSocialDeclarada.class)))
                .thenReturn(new CredencialSocialValidada(
                        "google",
                        "google-sub-123",
                        "social@eickrono.com",
                        "social.user",
                        "Social User",
                        "https://cdn.eickrono.store/avatar-social.png"
                ));
        when(localizadorLoginSocialProjeto.localizar(1L, "google", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(localizadorPerfilSistemaProjetoPorEmail.localizar(1L, "social@eickrono.com"))
                .thenReturn(Optional.of(new PerfilSistemaProjetoPorEmailResolvido(
                        UUID.fromString("92929292-9292-9292-9292-929292929292"),
                        "social@eickrono.com",
                        "pedrosotc"
                )));

        mockMvc.perform(post("/api/publica/sessoes/sociais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "provedor": "google",
                                  "tokenExterno": "google-token-externo",
                                  "dispositivo": {
                                    "plataforma": "ANDROID",
                                    "identificadorInstalacao": "instalacao-social",
                                    "modelo": "pixel",
                                    "fabricante": "google",
                                    "sistemaOperacional": "android",
                                    "versaoSistema": "15",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "ANDROID",
                                    "provedor": "GOOGLE_PLAY_INTEGRITY",
                                    "tipoComprovante": "TOKEN_INTEGRIDADE",
                                    "identificadorDesafio": "desafio-social",
                                    "desafioBase64": "ZGVzYWZpby1zb2NpYWw=",
                                    "conteudoComprovante": "dG9rZW0=",
                                    "geradoEm": "2026-03-26T20:00:00Z"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "ANDROID",
                                    "provedorAtestacao": "GOOGLE_PLAY_INTEGRITY",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "packageName": "store.eickrono.thimisu"
                                  }
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("social_sem_conta_local"))
                .andExpect(jsonPath("$.detalhes.acaoSugerida").value("ENTRAR_E_VINCULAR"))
                .andExpect(jsonPath("$.detalhes.provedor").value("google"))
                .andExpect(jsonPath("$.detalhes.identificadorExterno").value("google-sub-123"))
                .andExpect(jsonPath("$.detalhes.nomeExibicaoExterno").value("Social User"))
                .andExpect(jsonPath("$.detalhes.urlAvatarExterno")
                        .value("https://cdn.eickrono.store/avatar-social.png"))
                .andExpect(jsonPath("$.detalhes.emailContaExistente").value("social@eickrono.com"))
                .andExpect(jsonPath("$.detalhes.loginSugerido").value("pedrosotc"));

        Mockito.verifyNoInteractions(autenticacaoSessaoInternaServico);
    }
}
