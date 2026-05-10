package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.PerfilSistemaProjetoPorEmailResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.AvaliacaoSegurancaAplicativoService;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.ContextoSocialPendenteJdbc;
import com.eickrono.api.identidade.aplicacao.servico.LocalizadorPerfilSistemaProjetoPorEmailJdbc;
import com.eickrono.api.identidade.aplicacao.servico.RecuperacaoSenhaService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorContextoAutenticacaoService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorProjetoFluxoPublico;
import com.eickrono.api.identidade.aplicacao.servico.TokenDispositivoService;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.AtestacaoOperacaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CadastroApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CriarSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CriarSessaoSocialApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RenovarSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.SegurancaAplicativoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.SessaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.VinculoSocialPendenteApiRequest;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TipoComprovanteAtestacaoApp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class FluxoPublicoControllerTest {

    @Mock
    private CadastroContaInternaServico cadastroContaInternaServico;

    @Mock
    private AtestacaoAppServico atestacaoAppServico;

    @Mock
    private AvaliacaoSegurancaAplicativoService avaliacaoSegurancaAplicativoService;

    @Mock
    private AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;

    @Mock
    private ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService;

    @Mock
    private ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;

    @Mock
    private ContextoSocialPendenteJdbc contextoSocialPendenteJdbc;

    @Mock
    private LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail;

    @Mock
    private ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;

    @Mock
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Mock
    private RegistroDispositivoService registroDispositivoService;

    @Mock
    private RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;

    @Mock
    private TokenDispositivoService tokenDispositivoService;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private HttpServletRequest servletRequest;

    private FluxoPublicoController controller;

    @BeforeEach
    void setUp() {
        controller = new FluxoPublicoController(
                cadastroContaInternaServico,
                atestacaoAppServico,
                avaliacaoSegurancaAplicativoService,
                autenticacaoSessaoInternaServico,
                resolvedorContextoAutenticacaoService,
                clienteAdministracaoVinculosSociaisKeycloak,
                contextoSocialPendenteJdbc,
                localizadorPerfilSistemaProjetoPorEmail,
                resolvedorProjetoFluxoPublico,
                recuperacaoSenhaService,
                registroDispositivoService,
                registroDispositivoLoginSilenciosoService,
                tokenDispositivoService,
                jwtDecoder
        );
    }

    @Test
    void deveAtualizarContextosSociaisPendentesAoCriarCadastroComListaPlural() {
        setUp();
        CadastroApiRequest request = novoCadastroApiRequest(
                new VinculoSocialPendenteApiRequest(
                        "google",
                        "google-user-123",
                        "google-contexto-1",
                        "ana.google",
                        "ana.social@example.com",
                        "Ana Social",
                        "https://cdn.test/avatar-google.png"
                ),
                List.of(
                        new VinculoSocialPendenteApiRequest(
                                "google",
                                "google-user-123",
                                "google-contexto-1",
                                "ana.google",
                                "ana.social@example.com",
                                "Ana Social",
                                "https://cdn.test/avatar-google.png"
                        ),
                        new VinculoSocialPendenteApiRequest(
                                "apple",
                                "apple-user-456",
                                "apple-contexto-2",
                                "ana.apple",
                                null,
                                null,
                                "https://cdn.test/avatar-apple.png"
                        )
                )
        );
        ProjetoFluxoPublicoResolvido projeto = new ProjetoFluxoPublicoResolvido(
                77L,
                "eickrono-thimisu-app",
                "Thimisu",
                "APP",
                "Thimisu",
                "MOBILE",
                false
        );
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app")).thenReturn(projeto);
        when(cadastroContaInternaServico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                SexoPessoaCadastro.FEMININO,
                "BR",
                LocalDate.parse("1994-08-17"),
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte123",
                "eickrono-thimisu-app",
                null,
                "JUnit"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "sub-ana",
                        "ana@eickrono.com",
                        true
                ));

        controller.criarCadastro(request, servletRequest);

        verify(resolvedorProjetoFluxoPublico).resolverAtivo("eickrono-thimisu-app");
        verify(contextoSocialPendenteJdbc).registrarOuAtualizar(
                projeto,
                "google",
                "google-user-123",
                "ana.social@example.com",
                "ana.google",
                "Ana Social",
                "https://cdn.test/avatar-google.png",
                null,
                null
        );
        verify(contextoSocialPendenteJdbc).registrarOuAtualizar(
                projeto,
                "apple",
                "apple-user-456",
                "ana@eickrono.com",
                "ana.apple",
                null,
                "https://cdn.test/avatar-apple.png",
                null,
                null
        );
    }

    @Test
    void deveAtualizarContextoSocialPendenteAoCriarCadastroComCampoLegadoSingular() {
        CadastroApiRequest request = novoCadastroApiRequest(
                new VinculoSocialPendenteApiRequest(
                        "google",
                        "google-user-123",
                        "google-contexto-1",
                        "ana.google",
                        null,
                        "Ana Social",
                        null
                ),
                null
        );
        ProjetoFluxoPublicoResolvido projeto = new ProjetoFluxoPublicoResolvido(
                77L,
                "eickrono-thimisu-app",
                "Thimisu",
                "APP",
                "Thimisu",
                "MOBILE",
                false
        );
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app")).thenReturn(projeto);
        when(cadastroContaInternaServico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                SexoPessoaCadastro.FEMININO,
                "BR",
                LocalDate.parse("1994-08-17"),
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte123",
                "eickrono-thimisu-app",
                null,
                "JUnit"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "sub-ana",
                        "ana@eickrono.com",
                        true
                ));

        controller.criarCadastro(request, servletRequest);

        verify(contextoSocialPendenteJdbc).registrarOuAtualizar(
                projeto,
                "google",
                "google-user-123",
                "ana@eickrono.com",
                "ana.google",
                "Ana Social",
                null,
                null,
                null
        );
    }

    private CadastroApiRequest novoCadastroApiRequest(
            final VinculoSocialPendenteApiRequest vinculoSocialPendente,
            final List<VinculoSocialPendenteApiRequest> vinculosSociaisPendentes) {
        return new CadastroApiRequest(
                "eickrono-thimisu-app",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                SexoPessoaCadastro.FEMININO,
                "BR",
                LocalDate.parse("1994-08-17"),
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte123",
                "SenhaForte123",
                true,
                true,
                PlataformaAtestacaoApp.IOS,
                vinculoSocialPendente,
                vinculosSociaisPendentes,
                new AtestacaoOperacaoApiRequest(
                        PlataformaAtestacaoApp.IOS,
                        ProvedorAtestacaoApp.APPLE_APP_ATTEST,
                        TipoComprovanteAtestacaoApp.OBJETO_ASSERCAO,
                        "desafio",
                        "ZGVzYWZpbw==",
                        "Y29tcHJvdmFudGU=",
                        OffsetDateTime.parse("2026-03-26T20:00:00Z"),
                        "chave"
                ),
                new SegurancaAplicativoApiRequest(
                        "IOS",
                        "APPLE_APP_ATTEST",
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        List.<String>of(),
                        0,
                        null,
                        "com.eickrono.thimisu",
                        "TEAM123",
                        null
                )
        );
    }

    @Test
    void deveCancelarContextoSocialPendenteNoProjetoAtual() {
        UUID contextoId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        ProjetoFluxoPublicoResolvido projeto = new ProjetoFluxoPublicoResolvido(
                77L,
                "eickrono-thimisu-app",
                "Thimisu",
                "APP",
                "Thimisu",
                "MOBILE",
                false
        );
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app")).thenReturn(projeto);

        controller.cancelarContextoSocialPendente(contextoId, "eickrono-thimisu-app");

        verify(resolvedorProjetoFluxoPublico).resolverAtivo("eickrono-thimisu-app");
        verify(contextoSocialPendenteJdbc).cancelar(
                contextoId,
                77L,
                "USUARIO_DESISTIU"
        );
    }

    @Test
    void deveRecomporSessaoLocalNaRenovacaoQuandoNaoHouverTokenDispositivo() {
        RenovarSessaoApiRequest request = new RenovarSessaoApiRequest(
                "refresh-token",
                null,
                "eickrono-thimisu-app",
                new DispositivoSessaoApiRequest(
                        "IOS",
                        "eickrono-thimisu-app",
                        "instalacao-1",
                        "iphone17,1",
                        "apple",
                        "ios",
                        "18.0",
                        "1.0.0"
                )
        );
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-jwt",
                "refresh-token-novo",
                1800L
        );
        Jwt jwtSessaoCentral = Jwt.withTokenValue("access-jwt")
                .header("alg", "none")
                .subject("usuario-xyz")
                .claim("email", "teste@eickrono.com")
                .build();
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                123L,
                "usuario-xyz",
                "teste@eickrono.com",
                "Usuario Teste",
                null,
                "LIBERADO"
        );
        DispositivoSessaoRegistrado dispositivoRegistrado = new DispositivoSessaoRegistrado(
                "device-token",
                OffsetDateTime.parse("2026-05-05T18:00:00Z")
        );
        when(autenticacaoSessaoInternaServico.renovar("refresh-token", null)).thenReturn(sessao);
        when(jwtDecoder.decode("access-jwt")).thenReturn(jwtSessaoCentral);
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-xyz"))
                .thenReturn(Optional.of(contexto));
        when(registroDispositivoLoginSilenciosoService.registrar(contexto, request.dispositivo()))
                .thenReturn(dispositivoRegistrado);

        SessaoApiResposta resposta = controller.renovarSessao(request);

        assertThat(resposta.autenticado()).isTrue();
        assertThat(resposta.accessToken()).isEqualTo("access-jwt");
        assertThat(resposta.refreshToken()).isEqualTo("refresh-token-novo");
        assertThat(resposta.tokenDispositivo()).isEqualTo("device-token");
        assertThat(resposta.tokenDispositivoExpiraEm()).isEqualTo(OffsetDateTime.parse("2026-05-05T18:00:00Z"));
        assertThat(resposta.statusPerfilSistema()).isEqualTo("LIBERADO");
        assertThat(resposta.emailPrincipal()).isEqualTo("teste@eickrono.com");
        assertThat(resposta.podeOferecerBiometria()).isTrue();
    }

    @Test
    void devePreservarContextoDaSessaoNaRenovacaoQuandoTokenDispositivoJaForInformado() {
        RenovarSessaoApiRequest request = new RenovarSessaoApiRequest(
                "refresh-token",
                "device-token-atual",
                null,
                null
        );
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-jwt",
                "refresh-token-novo",
                1800L
        );
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                123L,
                "usuario-xyz",
                "teste@eickrono.com",
                "Usuario Teste",
                null,
                "PENDENTE_LIBERACAO_PRODUTO"
        );
        when(autenticacaoSessaoInternaServico.renovar("refresh-token", "device-token-atual")).thenReturn(sessao);
        when(tokenDispositivoService.validarTokenAtivoSemUsuario("device-token-atual"))
                .thenReturn(Optional.of(new TokenDispositivoService.TokenDispositivoValidado(
                        "usuario-xyz",
                        OffsetDateTime.parse("2026-05-05T18:00:00Z")
                )));
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-xyz"))
                .thenReturn(Optional.of(contexto));

        SessaoApiResposta resposta = controller.renovarSessao(request);

        assertThat(resposta.autenticado()).isTrue();
        assertThat(resposta.tokenDispositivo()).isEqualTo("device-token-atual");
        assertThat(resposta.tokenDispositivoExpiraEm()).isEqualTo(OffsetDateTime.parse("2026-05-05T18:00:00Z"));
        assertThat(resposta.statusPerfilSistema()).isEqualTo("PENDENTE_LIBERACAO_PRODUTO");
        assertThat(resposta.emailPrincipal()).isEqualTo("teste@eickrono.com");
        verifyNoInteractions(jwtDecoder);
        verifyNoInteractions(registroDispositivoLoginSilenciosoService);
    }

    @Test
    void devePropagarDispositivoNaoLiberadoNaRenovacaoQuandoBootstrapNaoConseguirRecomporSessao() {
        RenovarSessaoApiRequest request = new RenovarSessaoApiRequest(
                "refresh-token",
                null,
                "eickrono-thimisu-app",
                new DispositivoSessaoApiRequest(
                        "IOS",
                        "eickrono-thimisu-app",
                        "instalacao-1",
                        "iphone17,1",
                        "apple",
                        "ios",
                        "18.0",
                        "1.0.0"
                )
        );
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-jwt",
                "refresh-token-novo",
                1800L
        );
        Jwt jwtSessaoCentral = Jwt.withTokenValue("access-jwt")
                .header("alg", "none")
                .subject("usuario-xyz")
                .claim("email", "teste@eickrono.com")
                .build();
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                123L,
                "usuario-xyz",
                "teste@eickrono.com",
                "Usuario Teste",
                null,
                "LIBERADO"
        );
        when(autenticacaoSessaoInternaServico.renovar("refresh-token", null)).thenReturn(sessao);
        when(jwtDecoder.decode("access-jwt")).thenReturn(jwtSessaoCentral);
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-xyz"))
                .thenReturn(Optional.of(contexto));
        when(registroDispositivoLoginSilenciosoService.registrar(contexto, request.dispositivo()))
                .thenThrow(new FluxoPublicoException(
                        org.springframework.http.HttpStatus.LOCKED,
                        "dispositivo_nao_liberado",
                        "Este dispositivo não está liberado para uso com a conta."
                ));
        when(registroDispositivoService.solicitarRegistroParaSessao(contexto, request.dispositivo()))
                .thenReturn(new RegistroDispositivoResponse(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        OffsetDateTime.parse("2026-05-05T18:15:00Z"),
                        StatusRegistroDispositivo.PENDENTE,
                        java.util.List.of(CanalVerificacao.EMAIL)
                ));

        SessaoApiResposta resposta = controller.renovarSessao(request);

        assertThat(resposta.autenticado()).isTrue();
        assertThat(resposta.tokenDispositivo()).isNull();
        assertThat(resposta.registroDispositivoId())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(resposta.registroDispositivoExpiraEm())
                .isEqualTo(OffsetDateTime.parse("2026-05-05T18:15:00Z"));
        assertThat(resposta.statusRegistroDispositivo()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(resposta.canaisConfirmacao()).containsExactly(CanalVerificacao.EMAIL);
        assertThat(resposta.podeOferecerBiometria()).isFalse();
        assertThat(resposta.podeOferecerVinculacaoSocial()).isFalse();
    }

    @Test
    void deveConcluirSessaoLocalNoLoginSocialQuandoPerfilLocalExistir() {
        CriarSessaoSocialApiRequest request = criarSessaoSocialRequest();
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-social",
                "refresh-social",
                1800L
        );
        Jwt jwtSessaoCentral = Jwt.withTokenValue("access-social")
                .header("alg", "none")
                .subject("usuario-social")
                .claim("email", "social@eickrono.com")
                .build();
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                87L,
                "usuario-social",
                "social@eickrono.com",
                "Usuario Social",
                null,
                "LIBERADO"
        );
        DispositivoSessaoRegistrado dispositivoRegistrado = new DispositivoSessaoRegistrado(
                "device-social",
                OffsetDateTime.parse("2026-05-05T19:00:00Z")
        );
        when(autenticacaoSessaoInternaServico.autenticarSocial("google", "google-token")).thenReturn(sessao);
        when(jwtDecoder.decode("access-social")).thenReturn(jwtSessaoCentral);
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-social"))
                .thenReturn(Optional.of(contexto));
        when(registroDispositivoLoginSilenciosoService.registrar(contexto, request.dispositivo()))
                .thenReturn(dispositivoRegistrado);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        SessaoApiResposta resposta = controller.criarSessaoSocial(request, servletRequest);

        assertThat(resposta.autenticado()).isTrue();
        assertThat(resposta.accessToken()).isEqualTo("access-social");
        assertThat(resposta.refreshToken()).isEqualTo("refresh-social");
        assertThat(resposta.tokenDispositivo()).isEqualTo("device-social");
        assertThat(resposta.statusPerfilSistema()).isEqualTo("LIBERADO");
        assertThat(resposta.emailPrincipal()).isEqualTo("social@eickrono.com");
        assertThat(resposta.podeOferecerBiometria()).isTrue();
    }

    @Test
    void devePropagarDispositivoNaoLiberadoNoLoginSocialQuandoSessaoLocalNaoPuderSerConcluida() {
        CriarSessaoSocialApiRequest request = criarSessaoSocialRequest();
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-social",
                "refresh-social",
                1800L
        );
        Jwt jwtSessaoCentral = Jwt.withTokenValue("access-social")
                .header("alg", "none")
                .subject("usuario-social")
                .claim("email", "social@eickrono.com")
                .build();
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                87L,
                "usuario-social",
                "social@eickrono.com",
                "Usuario Social",
                null,
                "LIBERADO"
        );
        when(autenticacaoSessaoInternaServico.autenticarSocial("google", "google-token")).thenReturn(sessao);
        when(jwtDecoder.decode("access-social")).thenReturn(jwtSessaoCentral);
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-social"))
                .thenReturn(Optional.of(contexto));
        when(registroDispositivoLoginSilenciosoService.registrar(contexto, request.dispositivo()))
                .thenThrow(new FluxoPublicoException(
                        org.springframework.http.HttpStatus.LOCKED,
                        "dispositivo_nao_liberado",
                        "Este dispositivo não está liberado para uso com a conta."
                ));
        when(registroDispositivoService.solicitarRegistroParaSessao(contexto, request.dispositivo()))
                .thenReturn(new RegistroDispositivoResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        OffsetDateTime.parse("2026-05-05T19:15:00Z"),
                        StatusRegistroDispositivo.PENDENTE,
                        java.util.List.of(CanalVerificacao.EMAIL)
                ));
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        SessaoApiResposta resposta = controller.criarSessaoSocial(request, servletRequest);

        assertThat(resposta.autenticado()).isTrue();
        assertThat(resposta.tokenDispositivo()).isNull();
        assertThat(resposta.registroDispositivoId())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(resposta.statusRegistroDispositivo()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(resposta.canaisConfirmacao()).containsExactly(CanalVerificacao.EMAIL);
        assertThat(resposta.podeOferecerBiometria()).isFalse();
        assertThat(resposta.podeOferecerVinculacaoSocial()).isFalse();
    }

    @Test
    void deveAbrirRegistroInterativoNoLoginPorSenhaQuandoSessaoLocalNaoPuderSerConcluida() {
        CriarSessaoApiRequest request = new CriarSessaoApiRequest(
                "eickrono-thimisu-app",
                "teste@eickrono.com",
                "Senha#123",
                null,
                new DispositivoSessaoApiRequest(
                        "IOS",
                        "eickrono-thimisu-app",
                        "instalacao-login",
                        "iphone17,1",
                        "apple",
                        "ios",
                        "18.0",
                        "1.0.0"
                ),
                new AtestacaoOperacaoApiRequest(
                        PlataformaAtestacaoApp.IOS,
                        ProvedorAtestacaoApp.APPLE_APP_ATTEST,
                        TipoComprovanteAtestacaoApp.OBJETO_ATESTACAO,
                        "desafio",
                        "ZGVzYWZpbw==",
                        "dG9rZW0=",
                        OffsetDateTime.parse("2026-05-05T18:00:00Z"),
                        null
                ),
                new SegurancaAplicativoApiRequest(
                        "IOS",
                        "APPLE_APP_ATTEST",
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        java.util.List.of(),
                        0,
                        "store.eickrono.thimisu",
                        null,
                        null,
                        null
                )
        );
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-login",
                "refresh-login",
                1800L
        );
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                123L,
                "usuario-xyz",
                "teste@eickrono.com",
                "Usuario Teste",
                null,
                "LIBERADO"
        );
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(autenticacaoSessaoInternaServico.autenticar("teste@eickrono.com", "Senha#123")).thenReturn(sessao);
        when(resolvedorContextoAutenticacaoService.buscarPorEmailPublicoPreferindoProduto("teste@eickrono.com"))
                .thenReturn(Optional.of(contexto));
        when(registroDispositivoLoginSilenciosoService.registrar(contexto, request.dispositivo()))
                .thenThrow(new FluxoPublicoException(
                        org.springframework.http.HttpStatus.LOCKED,
                        "dispositivo_nao_liberado",
                        "Este dispositivo não está liberado para uso com a conta."
                ));
        when(registroDispositivoService.solicitarRegistroParaSessao(contexto, request.dispositivo()))
                .thenReturn(new RegistroDispositivoResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        OffsetDateTime.parse("2026-05-05T18:20:00Z"),
                        StatusRegistroDispositivo.PENDENTE,
                        java.util.List.of(CanalVerificacao.EMAIL)
                ));

        SessaoApiResposta resposta = controller.criarSessao(request, servletRequest);

        assertThat(resposta.autenticado()).isTrue();
        assertThat(resposta.accessToken()).isEqualTo("access-login");
        assertThat(resposta.refreshToken()).isEqualTo("refresh-login");
        assertThat(resposta.tokenDispositivo()).isNull();
        assertThat(resposta.registroDispositivoId())
                .isEqualTo(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertThat(resposta.statusRegistroDispositivo()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(resposta.canaisConfirmacao()).containsExactly(CanalVerificacao.EMAIL);
        assertThat(resposta.podeOferecerBiometria()).isFalse();
        assertThat(resposta.podeOferecerVinculacaoSocial()).isFalse();
    }

    @Test
    void deveRetornarSocialSemContaLocalComEntrarEVincularNoLoginSocialQuandoContaLocalExistirPorEmail() {
        CriarSessaoSocialApiRequest request = criarSessaoSocialRequest();
        SessaoInternaAutenticada sessao = new SessaoInternaAutenticada(
                true,
                "Bearer",
                "access-social",
                "refresh-social",
                1800L
        );
        Jwt jwtSessaoCentral = Jwt.withTokenValue("access-social")
                .header("alg", "none")
                .subject("usuario-sem-conta")
                .claim("email", "social@eickrono.com")
                .claim("name", "Social User")
                .claim("picture", "https://cdn.eickrono.store/avatar-social.png")
                .build();
        ProjetoFluxoPublicoResolvido projeto = new ProjetoFluxoPublicoResolvido(
                1L,
                "eickrono-thimisu-app",
                "Thimisu",
                "APP",
                "Thimisu",
                "MOBILE",
                false
        );
        UUID contextoPendenteId = UUID.fromString("91919191-9191-9191-9191-919191919191");
        when(autenticacaoSessaoInternaServico.autenticarSocial("google", "google-token")).thenReturn(sessao);
        when(jwtDecoder.decode("access-social")).thenReturn(jwtSessaoCentral);
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("usuario-sem-conta"))
                .thenReturn(Optional.empty());
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app")).thenReturn(projeto);
        when(localizadorPerfilSistemaProjetoPorEmail.localizar(1L, "social@eickrono.com"))
                .thenReturn(Optional.of(new PerfilSistemaProjetoPorEmailResolvido(
                        UUID.fromString("92929292-9292-9292-9292-929292929292"),
                        "social@eickrono.com",
                        "pedrosotc"
                )));
        when(clienteAdministracaoVinculosSociaisKeycloak.listarIdentidadesFederadas("usuario-sem-conta"))
                .thenReturn(java.util.List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-123",
                        "social.user",
                        "Social User",
                        "https://cdn.eickrono.store/avatar-social.png"
                )));
        when(contextoSocialPendenteJdbc.registrarOuAtualizar(
                projeto,
                "google",
                "google-sub-123",
                "social@eickrono.com",
                "social.user",
                "Social User",
                "https://cdn.eickrono.store/avatar-social.png",
                UUID.fromString("92929292-9292-9292-9292-929292929292"),
                "pedrosotc"
        )).thenReturn(contextoPendenteId);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThatThrownBy(() -> controller.criarSessaoSocial(request, servletRequest))
                .isInstanceOf(FluxoPublicoException.class)
                .satisfies(excecao -> {
                    FluxoPublicoException erro = (FluxoPublicoException) excecao;
                    assertThat(erro.getCodigo()).isEqualTo("social_sem_conta_local");
                    assertThat(erro.getDetalhes())
                            .containsEntry("acaoSugerida", "ENTRAR_E_VINCULAR")
                            .containsEntry("loginSugerido", "pedrosotc")
                            .containsEntry("emailContaExistente", "social@eickrono.com")
                            .containsEntry("provedor", "google")
                            .containsEntry("identificadorExterno", "google-sub-123")
                            .containsEntry("nomeExibicaoExterno", "Social User")
                            .containsEntry("urlAvatarExterno", "https://cdn.eickrono.store/avatar-social.png")
                            .containsEntry("contextoSocialPendenteId", contextoPendenteId);
                });
    }

    private CriarSessaoSocialApiRequest criarSessaoSocialRequest() {
        return new CriarSessaoSocialApiRequest(
                "eickrono-thimisu-app",
                "google",
                "google-token",
                new DispositivoSessaoApiRequest(
                        "ANDROID",
                        null,
                        "instalacao-social",
                        "pixel",
                        "google",
                        "android",
                        "15",
                        "1.0.0"
                ),
                new AtestacaoOperacaoApiRequest(
                        PlataformaAtestacaoApp.ANDROID,
                        ProvedorAtestacaoApp.GOOGLE_PLAY_INTEGRITY,
                        TipoComprovanteAtestacaoApp.TOKEN_INTEGRIDADE,
                        "desafio",
                        "ZGVzYWZpbw==",
                        "dG9rZW0=",
                        OffsetDateTime.parse("2026-05-05T18:00:00Z"),
                        null
                ),
                new SegurancaAplicativoApiRequest(
                        "ANDROID",
                        "GOOGLE_PLAY_INTEGRITY",
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        java.util.List.of(),
                        0,
                        "store.eickrono.thimisu",
                        null,
                        null,
                        null
                )
        );
    }
}
