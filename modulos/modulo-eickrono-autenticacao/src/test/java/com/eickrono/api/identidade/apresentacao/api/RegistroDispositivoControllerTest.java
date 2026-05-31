package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.PerfilSistemaProjetoPorEmailResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.LocalizadorPerfilSistemaProjetoPorEmailJdbc;
import com.eickrono.api.identidade.aplicacao.servico.OfflineDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorProjetoFluxoPublico;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.apresentacao.dto.EventoOfflineDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistrarEventosOfflineRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoSessaoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RevogarTokenRequest;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TipoEventoOfflineDispositivo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class RegistroDispositivoControllerTest {

    @Mock
    private RegistroDispositivoService registroDispositivoService;

    @Mock
    private OfflineDispositivoService offlineDispositivoService;

    @Mock
    private RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;

    @Mock
    private CadastroContaInternaServico cadastroContaInternaServico;

    @Mock
    private ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;

    @Mock
    private ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;

    @Mock
    private LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail;

    private RegistroDispositivoController controller;

    @BeforeEach
    void setUp() {
        controller = new RegistroDispositivoController(
                registroDispositivoService,
                offlineDispositivoService,
                registroDispositivoLoginSilenciosoService,
                cadastroContaInternaServico,
                clienteAdministracaoVinculosSociaisKeycloak,
                resolvedorProjetoFluxoPublico,
                localizadorPerfilSistemaProjetoPorEmail
        );
    }

    @Test
    void deveDelegarSolicitacaoDeRegistroComJwtOpcional() {
        setUp();
        RegistroDispositivoRequest request = new RegistroDispositivoRequest();
        request.setEmail("teste@eickrono.com");
        request.setTelefone("+55-11-99999-0000");
        request.setFingerprint("ios|iphone17,1|device-1");
        request.setPlataforma("IOS");
        request.setVersaoAplicativo("1.0.0");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("usuario-xyz")
                .build();
        RegistroDispositivoResponse esperado = new RegistroDispositivoResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                OffsetDateTime.parse("2026-05-05T18:00:00Z"),
                StatusRegistroDispositivo.PENDENTE,
                List.of(CanalVerificacao.EMAIL)
        );
        when(registroDispositivoService.solicitarRegistro(request, Optional.of(jwt))).thenReturn(esperado);

        ResponseEntity<RegistroDispositivoResponse> resposta = controller.solicitarRegistro(request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(202);
        assertThat(resposta.getBody()).isEqualTo(esperado);
        verify(registroDispositivoService).solicitarRegistro(request, Optional.of(jwt));
    }

    @Test
    void deveDelegarConfirmacaoDeRegistroComJwtOpcional() {
        UUID registroId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ConfirmacaoRegistroRequest request = new ConfirmacaoRegistroRequest();
        request.setCodigoSms("123456");
        request.setCodigoEmail("654321");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("usuario-xyz")
                .build();
        ConfirmacaoRegistroResponse esperado = new ConfirmacaoRegistroResponse(
                "token-dispositivo",
                OffsetDateTime.parse("2026-05-05T19:00:00Z"),
                registroId,
                OffsetDateTime.parse("2026-05-05T18:05:00Z")
        );
        when(registroDispositivoService.confirmarRegistro(registroId, request, Optional.of(jwt))).thenReturn(esperado);

        ResponseEntity<ConfirmacaoRegistroResponse> resposta =
                controller.confirmarRegistro(registroId, request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(esperado);
        verify(registroDispositivoService).confirmarRegistro(registroId, request, Optional.of(jwt));
    }

    @Test
    void deveUsarRequestPadraoAoReenviarCodigosSemPayload() {
        UUID registroId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ResponseEntity<Void> resposta = controller.reenviarCodigos(registroId, null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(202);
        verify(registroDispositivoService).reenviarCodigos(
                eq(registroId),
                argThat(payload -> payload != null && payload.deveReenviarSms() && payload.deveReenviarEmail())
        );
    }

    @Test
    void deveRegistrarSessaoSilenciosaQuandoContaEstiverLiberada() {
        DispositivoSessaoApiRequest request = new DispositivoSessaoApiRequest(
                "IOS",
                "eickrono-thimisu-app",
                "instalacao-social-ok",
                "iphone17,1",
                "apple",
                "ios",
                "18.0",
                "1.0.0"
        );
        Jwt jwt = Jwt.withTokenValue("token")
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
                "PENDENTE_LIBERACAO_PRODUTO"
        );
        DispositivoSessaoRegistrado esperado = new DispositivoSessaoRegistrado(
                "token-dispositivo",
                OffsetDateTime.parse("2026-05-05T20:00:00Z")
        );
        when(cadastroContaInternaServico.buscarContextoCentralPorSubPublico("usuario-xyz")).thenReturn(Optional.of(contexto));
        when(registroDispositivoLoginSilenciosoService.registrar(contexto, request)).thenReturn(esperado);

        ResponseEntity<RegistroDispositivoSessaoResponse> resposta =
                controller.registrarSessaoSilenciosa(request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(new RegistroDispositivoSessaoResponse(
                "token-dispositivo",
                OffsetDateTime.parse("2026-05-05T20:00:00Z")
        ));
        verify(registroDispositivoLoginSilenciosoService).registrar(contexto, request);
    }

    @Test
    void deveBloquearRegistroSilenciosoQuandoContaNaoEstiverLiberada() {
        DispositivoSessaoApiRequest request = new DispositivoSessaoApiRequest(
                "IOS",
                "eickrono-thimisu-app",
                "instalacao-social-bloqueada",
                "iphone17,1",
                "apple",
                "ios",
                "18.0",
                "1.0.0"
        );
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("usuario-bloqueado")
                .claim("email", "bloqueado@eickrono.com")
                .build();
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                123L,
                "usuario-bloqueado",
                "bloqueado@eickrono.com",
                "Usuario Bloqueado",
                null,
                "AGUARDANDO_ANALISE"
        );
        when(cadastroContaInternaServico.buscarContextoCentralPorSubPublico("usuario-bloqueado")).thenReturn(Optional.of(contexto));

        assertThatThrownBy(() -> controller.registrarSessaoSilenciosa(request, jwt))
                .isInstanceOf(FluxoPublicoException.class)
                .satisfies(throwable -> {
                    FluxoPublicoException exception = (FluxoPublicoException) throwable;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCodigo()).isEqualTo("conta_nao_liberada");
                    assertThat(exception.getMessage())
                            .isEqualTo("A conta ainda não está liberada para utilizar o aplicativo.");
                });
    }

    @Test
    void deveRetornarSocialSemContaLocalComEntrarEVincularQuandoEmailJaExistirNoProjeto() {
        String sub = "usuario-sem-contexto";
        String email = "conta.existente@eickrono.com";
        UUID perfilSistemaId = UUID.randomUUID();
        DispositivoSessaoApiRequest request = new DispositivoSessaoApiRequest(
                "IOS",
                "eickrono-thimisu-app",
                "instalacao-social-vincular",
                "iphone17,1",
                "apple",
                "ios",
                "18.0",
                "1.0.0"
        );
        Jwt jwt = Jwt.withTokenValue("token-social")
                .header("alg", "none")
                .subject(sub)
                .claim("email", email)
                .claim("name", "Conta Existente")
                .build();
        ProjetoFluxoPublicoResolvido projeto = new ProjetoFluxoPublicoResolvido(
                1L,
                "eickrono-thimisu-app",
                "Eickrono Thimisu App",
                "APP_MOVEL",
                "Thimisu",
                "mobile",
                false
        );
        PerfilSistemaProjetoPorEmailResolvido contaExistente = new PerfilSistemaProjetoPorEmailResolvido(
                perfilSistemaId,
                email,
                "pedrosotc"
        );
        IdentidadeFederadaKeycloak identidadeGoogle = new IdentidadeFederadaKeycloak(
                ProvedorVinculoSocial.GOOGLE,
                "google-sub-2",
                email,
                "Conta Existente",
                "https://cdn.eickrono.test/google-vincular.png"
        );

        when(cadastroContaInternaServico.buscarContextoCentralPorSubPublico(sub)).thenReturn(Optional.empty());
        when(localizadorPerfilSistemaProjetoPorEmail.localizar(1L, email)).thenReturn(Optional.of(contaExistente));
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app")).thenReturn(projeto);
        when(clienteAdministracaoVinculosSociaisKeycloak.listarIdentidadesFederadas(sub))
                .thenReturn(List.of(identidadeGoogle));

        assertThatThrownBy(() -> controller.registrarSessaoSilenciosa(request, jwt))
                .isInstanceOf(FluxoPublicoException.class)
                .satisfies(throwable -> {
                    FluxoPublicoException exception = (FluxoPublicoException) throwable;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCodigo()).isEqualTo("social_sem_conta_local");
                    assertThat(exception.getMessage())
                            .isEqualTo("Ja existe uma conta neste projeto com o mesmo e-mail desta rede social. Deseja entrar e vincular agora?");
                    assertThat(exception.getDetalhes()).containsAllEntriesOf(Map.ofEntries(
                            Map.entry("sub", sub),
                            Map.entry("email", email),
                            Map.entry("acaoSugerida", "ENTRAR_E_VINCULAR"),
                            Map.entry("emailContaExistente", email),
                            Map.entry("loginSugerido", "pedrosotc"),
                            Map.entry("provedor", "google"),
                            Map.entry("identificadorExterno", "google-sub-2"),
                            Map.entry("nomeUsuarioExterno", email),
                            Map.entry("nomeExibicaoExterno", "Conta Existente"),
                            Map.entry("urlAvatarExterno", "https://cdn.eickrono.test/google-vincular.png")
                    ));
                });
    }

    @Test
    void deveUsarMotivoPadraoAoRevogarSemPayload() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("usuario-xyz")
                .build();

        ResponseEntity<Void> resposta = controller.revogarToken(jwt, "token-dispositivo", null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(204);
        verify(registroDispositivoService).revogarToken(
                "usuario-xyz",
                "token-dispositivo",
                MotivoRevogacaoToken.SOLICITACAO_CLIENTE
        );
    }

    @Test
    void deveUsarMotivoPadraoAoRevogarQuandoMotivoForDesconhecido() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("usuario-xyz")
                .build();
        RevogarTokenRequest request = new RevogarTokenRequest();
        request.setMotivo("motivo-inexistente");

        ResponseEntity<Void> resposta = controller.revogarToken(jwt, "token-dispositivo", request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(204);
        verify(registroDispositivoService).revogarToken(
                "usuario-xyz",
                "token-dispositivo",
                MotivoRevogacaoToken.SOLICITACAO_CLIENTE
        );
    }

    @Test
    void deveDelegarPoliticaOfflineDoBackend() {
        PoliticaOfflineDispositivoResponse politica = new PoliticaOfflineDispositivoResponse(
                true,
                480,
                true,
                List.of("TOKEN_REVOGADO"),
                List.of("MODO_OFFLINE_ATIVADO", "RECONCILIACAO_REALIZADA")
        );
        when(offlineDispositivoService.obterPolitica()).thenReturn(politica);

        ResponseEntity<PoliticaOfflineDispositivoResponse> resposta = controller.obterPoliticaOffline();

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(politica);
        verify(offlineDispositivoService).obterPolitica();
    }

    @Test
    void deveDelegarRegistroDeEventosOfflineComSubEDeviceToken() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("usuario-xyz")
                .build();
        RegistrarEventosOfflineRequest request = new RegistrarEventosOfflineRequest(List.of(
                new EventoOfflineDispositivoRequest(
                        TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO,
                        OffsetDateTime.parse("2026-03-11T18:00:00Z"),
                        "app entrou em modo offline"
                )
        ));

        ResponseEntity<Void> resposta = controller.registrarEventosOffline(jwt, "token-dispositivo", request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(202);
        verify(offlineDispositivoService).registrarEventosOffline(
                "usuario-xyz",
                "token-dispositivo",
                request
        );
    }
}
