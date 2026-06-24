package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.AvatarCadastroConfirmado;
import com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado;
import com.eickrono.api.identidade.aplicacao.modelo.CadastroKeycloakProvisionado;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroInternoRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroPublicoRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.PessoaCanonicaConfirmada;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.StatusCadastroPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.VinculoSocialConfirmadoCadastro;
import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.StatusCadastroConta;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import com.eickrono.api.identidade.dominio.repositorio.CadastroContaRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CadastroContaInternaServicoTest {

    @Mock
    private CadastroContaRepositorio cadastroContaRepositorio;

    @Mock
    private ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;

    @Mock
    private ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

    @Mock
    private CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail;

    @Mock
    private CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail;

    @Mock
    private ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;

    @Mock
    private ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico;

    @Mock
    private ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico;

    @Mock
    private DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService;

    @Mock
    private RegistradorPendenciaIntegracaoProdutoService registradorPendenciaIntegracaoProdutoService;

    @Mock
    private ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;

    @Mock
    private CadastroVinculoSocialConfirmadoJdbc cadastroVinculoSocialConfirmadoJdbc;

    @Mock
    private CadastroAvatarConfirmadoJdbc cadastroAvatarConfirmadoJdbc;

    @Mock
    private AvatarSocialProjetoJdbc avatarSocialProjetoJdbc;

    @Captor
    private ArgumentCaptor<CadastroConta> cadastroCaptor;

    @Captor
    private ArgumentCaptor<String> codigoCaptor;

    @Captor
    private ArgumentCaptor<IdentidadeFederadaKeycloak> identidadeFederadaCaptor;

    private CadastroContaInternaServico servico;
    private CadastroContaInternaServico servicoPublico;

    @BeforeEach
    void setUp() {
        DispositivoProperties dispositivoProperties = new DispositivoProperties();
        dispositivoProperties.getCodigo().setSegredoHmac("test-code-secret");
        dispositivoProperties.getCodigo().setTamanho(6);
        dispositivoProperties.getCodigo().setTentativasMaximas(5);
        dispositivoProperties.getCodigo().setReenviosMaximos(3);
        dispositivoProperties.getCodigo().setExpiracaoHoras(9);

        Clock clock = Clock.fixed(Instant.parse("2026-03-19T10:00:00Z"), ZoneOffset.UTC);
        servico = new CadastroContaInternaServico(
                cadastroContaRepositorio,
                clienteContextoPessoaPerfilSistema,
                clienteAdministracaoCadastroKeycloak,
                provisionadorPerfilSistemaServico,
                confirmadorPessoaCadastroServico,
                disponibilidadeUsuarioSistemaService,
                provisionamentoIdentidadeService,
                canalEnvioCodigoCadastroEmail,
                canalNotificacaoTentativaCadastroEmail,
                dispositivoProperties,
                clock,
                null
        );
        servicoPublico = new CadastroContaInternaServico(
                cadastroContaRepositorio,
                clienteContextoPessoaPerfilSistema,
                clienteAdministracaoCadastroKeycloak,
                provisionadorPerfilSistemaServico,
                confirmadorPessoaCadastroServico,
                disponibilidadeUsuarioSistemaService,
                provisionamentoIdentidadeService,
                canalEnvioCodigoCadastroEmail,
                canalNotificacaoTentativaCadastroEmail,
                dispositivoProperties,
                clock,
                registradorPendenciaIntegracaoProdutoService,
                resolvedorProjetoFluxoPublico,
                cadastroVinculoSocialConfirmadoJdbc,
                cadastroAvatarConfirmadoJdbc,
                avatarSocialProjetoJdbc
        );
    }

    @Test
    @DisplayName("deve consultar status público de cadastro pendente para continuar validação de e-mail")
    void deveConsultarStatusPublicoDeCadastroPendente() {
        UUID cadastroId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        CadastroConta cadastro = new CadastroConta(
                cadastroId,
                "sub-ana",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                "BR",
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash",
                OffsetDateTime.parse("2026-03-19T10:00:00Z"),
                OffsetDateTime.parse("2026-03-19T11:00:00Z"),
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.parse("2026-03-19T10:00:00Z"),
                OffsetDateTime.parse("2026-03-19T10:00:00Z")
        );
        when(cadastroContaRepositorio.findByCadastroId(cadastroId)).thenReturn(Optional.of(cadastro));

        StatusCadastroPublicoResolvido status = servicoPublico.consultarStatusCadastroPublico(cadastroId);

        assertThat(status.cadastroId()).isEqualTo(cadastroId);
        assertThat(status.emailPrincipal()).isEqualTo("ana@eickrono.com");
        assertThat(status.telefonePrincipal()).isEqualTo("+5511999999999");
        assertThat(status.emailConfirmado()).isFalse();
        assertThat(status.telefoneConfirmado()).isFalse();
        assertThat(status.telefoneObrigatorio()).isFalse();
        assertThat(status.liberadoParaLogin()).isFalse();
        assertThat(status.proximoPasso()).isEqualTo("VALIDAR_CONTATOS");
    }

    @Test
    @DisplayName("deve criar cadastro interno pendente e enviar o código de confirmação por e-mail")
    void deveCriarCadastroInternoPendente() {
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CadastroInternoRealizado resultado = servico.cadastrar(
                "Ana Souza",
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.WHATSAPP,
                "SenhaForte@123",
                "identidade-servidor",
                "127.0.0.1",
                "JUnit"
        );

        assertThat(resultado.subjectRemoto()).isEqualTo("sub-ana");
        assertThat(resultado.emailPrincipal()).isEqualTo("ana@eickrono.com");
        assertThat(resultado.verificacaoEmailObrigatoria()).isTrue();

        verify(cadastroContaRepositorio).save(cadastroCaptor.capture());
        assertThat(cadastroCaptor.getValue().getStatus().name()).isEqualTo("PENDENTE_EMAIL");
        assertThat(cadastroCaptor.getValue().getSistemaSolicitante()).isEqualTo("identidade-servidor");
        assertThat(cadastroCaptor.getValue().getTelefonePrincipal()).isEqualTo("+5511999999999");
        assertThat(cadastroCaptor.getValue().getCanalValidacaoTelefone())
                .isEqualTo(CanalValidacaoTelefoneCadastro.WHATSAPP);

        verify(provisionamentoIdentidadeService).provisionarCadastroPendente(
                "sub-ana",
                "ana@eickrono.com",
                "Ana Souza",
                cadastroCaptor.getValue().getCriadoEm()
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        assertThat(codigoCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    @DisplayName("deve confirmar o e-mail do cadastro pendente e liberar autenticação")
    void deveConfirmarEmailDoCadastro() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });

        CadastroInternoRealizado cadastro = servico.cadastrar(
                "Ana Souza",
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "identidade-servidor",
                "127.0.0.1",
                "JUnit"
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        ConfirmacaoEmailCadastroInternoRealizada confirmacao = servico.confirmarEmail(
                cadastro.cadastroId(),
                codigoCaptor.getValue()
        );

        assertThat(confirmacao.emailConfirmado()).isTrue();
        assertThat(confirmacao.podeAutenticar()).isTrue();
        assertThat(salvo.get().emailJaConfirmado()).isTrue();
        verify(clienteAdministracaoCadastroKeycloak).confirmarEmailEAtivarUsuario(
                eq("sub-ana"),
                eq("Ana Souza"),
                isNull()
        );
        verify(provisionamentoIdentidadeService).confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        );
    }

    @Test
    @DisplayName("deve reenviar o código do cadastro pendente e atualizar o controle de reenvios")
    void deveReenviarCodigoEmailDoCadastro() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });

        CadastroInternoRealizado cadastro = servico.cadastrar(
                "Ana Souza",
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "identidade-servidor",
                "127.0.0.1",
                "JUnit"
        );
        String hashAnterior = salvo.get().getCodigoEmailHash();
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        servico.reenviarCodigoEmail(cadastro.cadastroId());

        assertThat(salvo.get().getReenviosEmail()).isEqualTo(1);
        assertThat(salvo.get().getCodigoEmailHash()).isNotEqualTo(hashAnterior);
        verify(canalEnvioCodigoCadastroEmail, times(2)).enviar(any(CadastroConta.class), codigoCaptor.capture());
        assertThat(codigoCaptor.getAllValues()).hasSize(2);
        assertThat(codigoCaptor.getAllValues().get(0)).matches("\\d{6}");
        assertThat(codigoCaptor.getAllValues().get(1)).matches("\\d{6}");
        assertThat(codigoCaptor.getAllValues().get(1)).isNotEqualTo(codigoCaptor.getAllValues().get(0));
    }

    @Test
    @DisplayName("deve rejeitar o cadastro publico quando o usuário já estiver indisponível")
    void deveRejeitarCadastroPublicoComUsuarioIndisponivel() {
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(false);

        assertThatThrownBy(() -> servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "Ana.Souza",
                null,
                null,
                null,
                "ana+novo@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit"
        ))
                .isInstanceOf(FluxoPublicoException.class)
                .hasMessage("Este usuário não está disponível.");

        verify(clienteAdministracaoCadastroKeycloak, never()).criarUsuarioPendente(any(), any(), any());
        verify(canalNotificacaoTentativaCadastroEmail, never()).notificar(any());
    }

    @Test
    @DisplayName("deve informar a disponibilidade pública do usuário quando ele estiver livre")
    void deveInformarDisponibilidadePublicaDoUsuario() {
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);

        boolean disponivel = servicoPublico.identificadorPublicoSistemaDisponivelPublico(" Ana.Souza ");

        assertThat(disponivel).isTrue();
        verify(disponibilidadeUsuarioSistemaService)
                .identificadorPublicoSistemaDisponivel("ana.souza", "eickrono-thimisu-app");
    }

    @Test
    @DisplayName("deve informar a disponibilidade pública do usuário por sistema explícito")
    void deveInformarDisponibilidadePublicaDoUsuarioPorSistema() {
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);

        boolean disponivel = servicoPublico.identificadorPublicoSistemaDisponivelPublico(
                " Ana.Souza ",
                "eickrono-thimisu-app"
        );

        assertThat(disponivel).isTrue();
        verify(disponibilidadeUsuarioSistemaService)
                .identificadorPublicoSistemaDisponivel("ana.souza", "eickrono-thimisu-app");
    }

    @Test
    @DisplayName("deve confirmar pessoa canonica antes de provisionar o perfil do sistema no fluxo publico")
    void deveConfirmarPessoaCanonicaAntesDeProvisionarPerfilNoFluxoPublico() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });
        when(confirmadorPessoaCadastroServico.confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        ))
                .thenReturn(new PessoaCanonicaConfirmada(77L, "sub-ana", "ana@eickrono.com"));
        when(provisionadorPerfilSistemaServico.provisionarCadastroConfirmado(any(CadastroConta.class), eq(77L)))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado(
                        "usuario-001",
                        "LIBERADO"
                ));

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit"
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        ConfirmacaoEmailCadastroPublicoRealizada confirmacao = servicoPublico.confirmarEmailPublico(
                cadastro.cadastroId(),
                codigoCaptor.getValue()
        );

        assertThat(confirmacao.perfilSistemaId()).isEqualTo("usuario-001");
        assertThat(confirmacao.proximoPasso()).isEqualTo("LOGIN");
        assertThat(salvo.get().getPessoaIdPerfil()).isEqualTo(77L);
        InOrder inOrder = org.mockito.Mockito.inOrder(
                confirmadorPessoaCadastroServico,
                provisionadorPerfilSistemaServico
        );
        inOrder.verify(confirmadorPessoaCadastroServico).confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        );
        inOrder.verify(provisionadorPerfilSistemaServico).provisionarCadastroConfirmado(salvo.get(), 77L);
    }

    @Test
    @DisplayName("deve vincular e consumir todos os vínculos sociais confirmados do cadastro ao confirmar o e-mail")
    void deveVincularVinculosSociaisConfirmadosAoConfirmarCadastroPublico() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        )).thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });
        when(confirmadorPessoaCadastroServico.confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        )).thenReturn(new PessoaCanonicaConfirmada(77L, "sub-ana", "ana@eickrono.com"));
        when(provisionadorPerfilSistemaServico.provisionarCadastroConfirmado(any(CadastroConta.class), eq(77L)))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado(
                        "usuario-001",
                        "LIBERADO"
                ));
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app"))
                .thenReturn(new ProjetoFluxoPublicoResolvido(
                        99L,
                        "eickrono-thimisu-app",
                        "Thimisu",
                        "APP",
                        "Thimisu",
                        "MOBILE",
                        false
                ));
        List<VinculoSocialConfirmadoCadastro> vinculosSociaisConfirmados = List.of(
                new VinculoSocialConfirmadoCadastro(
                        "google",
                        "google-123",
                        "ana.google",
                        "ana.google@social.test",
                        "Ana Google",
                        "https://img/google.png",
                        true
                ),
                new VinculoSocialConfirmadoCadastro(
                        "apple",
                        "apple-123",
                        "ana.apple",
                        null,
                        "Ana Apple",
                        "https://img/apple.png",
                        false
                )
        );

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                vinculosSociaisConfirmados
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        verify(cadastroVinculoSocialConfirmadoJdbc).registrar(cadastro.cadastroId(), vinculosSociaisConfirmados);
        when(cadastroVinculoSocialConfirmadoJdbc.listarAtivos(cadastro.cadastroId()))
                .thenReturn(vinculosSociaisConfirmados);
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        servicoPublico.confirmarEmailPublico(cadastro.cadastroId(), codigoCaptor.getValue());

        verify(clienteAdministracaoCadastroKeycloak, times(2))
                .vincularIdentidadeFederada(eq("sub-ana"), identidadeFederadaCaptor.capture());
        assertThat(identidadeFederadaCaptor.getAllValues())
                .extracting(IdentidadeFederadaKeycloak::identificadorCanonico)
                .containsExactly("google-123", "apple-123");
        verify(avatarSocialProjetoJdbc).sincronizar(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq(99L),
                eq(salvo.get().getCriadoEm()),
                any(),
                eq("ana.souza"),
                any()
        );
        verify(avatarSocialProjetoJdbc).definirAvatarSocial(
                eq("sub-ana"),
                eq(99L),
                eq(com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial.GOOGLE),
                any()
        );
        verify(cadastroVinculoSocialConfirmadoJdbc).consumir(cadastro.cadastroId());
    }

    @Test
    @DisplayName("deve falhar sem consumir vínculo social confirmado quando materialização social falhar")
    void deveFalharSemConsumirVinculoSocialConfirmadoQuandoMaterializacaoFalhar() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        )).thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });
        when(confirmadorPessoaCadastroServico.confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        )).thenReturn(new PessoaCanonicaConfirmada(77L, "sub-ana", "ana@eickrono.com"));
        when(provisionadorPerfilSistemaServico.provisionarCadastroConfirmado(any(CadastroConta.class), eq(77L)))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado(
                        "usuario-001",
                        "LIBERADO"
                ));
        List<VinculoSocialConfirmadoCadastro> vinculosSociaisConfirmados = List.of(
                new VinculoSocialConfirmadoCadastro(
                        "google",
                        "google-123",
                        "ana.google",
                        "ana.google@social.test",
                        "Ana Google",
                        "https://img/google.png",
                        true
                )
        );

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                vinculosSociaisConfirmados
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        when(cadastroVinculoSocialConfirmadoJdbc.listarAtivos(cadastro.cadastroId()))
                .thenReturn(vinculosSociaisConfirmados);
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));
        doThrow(new IllegalStateException("Keycloak indisponivel"))
                .when(clienteAdministracaoCadastroKeycloak)
                .vincularIdentidadeFederada(eq("sub-ana"), any(IdentidadeFederadaKeycloak.class));

        assertThatThrownBy(() -> servicoPublico.confirmarEmailPublico(cadastro.cadastroId(), codigoCaptor.getValue()))
                .isInstanceOf(FluxoPublicoException.class)
                .extracting("codigo")
                .isEqualTo("vinculo_social_confirmado_nao_materializado");
        assertThat(salvo.get().emailJaConfirmado()).isFalse();
        verify(cadastroVinculoSocialConfirmadoJdbc, never()).consumir(cadastro.cadastroId());
        verify(avatarSocialProjetoJdbc, never()).definirAvatarSocial(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deve registrar avatar local do cadastro e materializar na confirmação de e-mail")
    void deveRegistrarAvatarLocalConfirmadoEConsumirNaConfirmacaoEmail() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        )).thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });
        when(confirmadorPessoaCadastroServico.confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        )).thenReturn(new PessoaCanonicaConfirmada(77L, "sub-ana", "ana@eickrono.com"));
        when(provisionadorPerfilSistemaServico.provisionarCadastroConfirmado(any(CadastroConta.class), eq(77L)))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado(
                        "usuario-001",
                        "LIBERADO"
                ));
        when(resolvedorProjetoFluxoPublico.resolverAtivo("eickrono-thimisu-app"))
                .thenReturn(new ProjetoFluxoPublicoResolvido(
                        99L,
                        "eickrono-thimisu-app",
                        "Thimisu",
                        "APP",
                        "Thimisu",
                        "MOBILE",
                        false
                ));
        List<AvatarCadastroConfirmado> avatares = List.of(new AvatarCadastroConfirmado(
                "THIMISU",
                "https://cdn.eickrono.test/avatar/ana.png",
                "usuarios/sub-ana/avatar/thimisu.png",
                null,
                "image/png",
                12345L,
                "hash-avatar",
                "versao-avatar",
                null,
                true
        ));

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                List.of(),
                avatares
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        verify(cadastroAvatarConfirmadoJdbc).registrar(cadastro.cadastroId(), avatares);
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        servicoPublico.confirmarEmailPublico(cadastro.cadastroId(), codigoCaptor.getValue());

        verify(cadastroAvatarConfirmadoJdbc).consumirParaUsuario(
                eq(cadastro.cadastroId()),
                eq("sub-ana"),
                eq(99L),
                any()
        );
    }

    @Test
    @DisplayName("deve remover usuário pendente do Keycloak quando cadastro público falhar após criar o usuário remoto")
    void deveCompensarUsuarioPendenteKeycloakQuandoCadastroPublicoFalharAposCriarUsuarioRemoto() {
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        )).thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AvatarCadastroConfirmado> avatares = List.of(new AvatarCadastroConfirmado(
                "THIMISU",
                "https://cdn.eickrono.test/avatar/ana.png",
                "usuarios/sub-ana/avatar/thimisu.png",
                null,
                "image/png",
                12345L,
                "hash-avatar",
                "versao-avatar",
                null,
                true
        ));
        doThrow(new IllegalStateException("falha avatar"))
                .when(cadastroAvatarConfirmadoJdbc)
                .registrar(any(), eq(avatares));

        assertThatThrownBy(() -> servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                List.of(),
                avatares
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha avatar");

        verify(clienteAdministracaoCadastroKeycloak).removerUsuarioPendente("sub-ana");
        verify(canalEnvioCodigoCadastroEmail, never()).enviar(any(), any());
    }

    @Test
    @DisplayName("deve rejeitar mais de uma opção de avatar preferida no cadastro")
    void deveRejeitarPreferenciaDuplicadaEntreAvatarSocialELocal() {
        List<VinculoSocialConfirmadoCadastro> vinculosSociais = List.of(new VinculoSocialConfirmadoCadastro(
                "google",
                "google-123",
                "ana.google",
                "ana.google@social.test",
                "Ana Google",
                "https://img/google.png",
                true
        ));
        List<AvatarCadastroConfirmado> avatares = List.of(new AvatarCadastroConfirmado(
                "THIMISU",
                "https://cdn.eickrono.test/avatar/ana.png",
                "usuarios/sub-ana/avatar/thimisu.png",
                null,
                "image/png",
                12345L,
                "hash-avatar",
                "versao-avatar",
                null,
                true
        ));

        assertThatThrownBy(() -> servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                vinculosSociais,
                avatares
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Apenas uma opção de avatar confirmada pode ser preferida.");
    }

    @Test
    @DisplayName("deve registrar pendencia e concluir a parte central quando o provisionamento do produto falhar com erro toleravel")
    void deveRegistrarPendenciaQuandoProvisionamentoDoProdutoFalharComErroToleravel() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza",
                "ana@eickrono.com",
                "SenhaForte@123"
        )).thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });
        when(confirmadorPessoaCadastroServico.confirmarEmailCadastro(
                eq("sub-ana"),
                eq("ana@eickrono.com"),
                eq("Ana Souza"),
                any()
        ))
                .thenReturn(new PessoaCanonicaConfirmada(77L, "sub-ana", "ana@eickrono.com"));
        when(provisionadorPerfilSistemaServico.provisionarCadastroConfirmado(any(CadastroConta.class), eq(77L)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "produto indisponivel"));

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit"
        );
        verify(canalEnvioCodigoCadastroEmail).enviar(any(CadastroConta.class), codigoCaptor.capture());
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        ConfirmacaoEmailCadastroPublicoRealizada confirmacao = servicoPublico.confirmarEmailPublico(
                cadastro.cadastroId(),
                codigoCaptor.getValue()
        );

        assertThat(confirmacao.perfilSistemaId()).isEmpty();
        assertThat(confirmacao.statusPerfilSistema()).isEqualTo("PENDENTE_LIBERACAO_PRODUTO");
        assertThat(confirmacao.podeAutenticar()).isTrue();
        assertThat(confirmacao.proximoPasso()).isEqualTo("LOGIN");
        assertThat(salvo.get().getPessoaIdPerfil()).isEqualTo(77L);
        assertThat(salvo.get().getPerfilSistemaId()).isNull();
        verify(registradorPendenciaIntegracaoProdutoService).registrarProvisionamentoPerfilSistema(
                salvo.get(),
                77L,
                "PROVISIONAMENTO_PERFIL_SISTEMA_HTTP_502",
                "produto indisponivel"
        );
        verify(clienteAdministracaoCadastroKeycloak).confirmarEmailEAtivarUsuario(
                eq("sub-ana"),
                eq("Ana Souza"),
                isNull()
        );
    }

    @Test
    @DisplayName("deve responder genericamente e avisar por e-mail quando já existir conta ativa para o e-mail")
    void deveAvisarPorEmailQuandoJaExistirContaAtivaNoEndereco() {
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        10L,
                        "sub-ana",
                        "ana@eickrono.com",
                        "Ana Souza",
                        "usuario-001",
                        "LIBERADO"
                )));

        assertThatThrownBy(() -> servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit"
        ))
                .isInstanceOf(FluxoPublicoException.class)
                .hasMessage("Não foi possível concluir o cadastro com os dados informados.");

        verify(canalNotificacaoTentativaCadastroEmail).notificar("ana@eickrono.com");
        verify(clienteAdministracaoCadastroKeycloak, never()).criarUsuarioPendente(any(), any(), any());
    }

    @Test
    @DisplayName("deve tolerar indisponibilidade do contexto do produto durante a validacao de duplicidade por email")
    void deveTolerarIndisponibilidadeDoContextoDoProdutoNaDuplicidadeDeEmail() {
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com"))
                .thenThrow(new IllegalStateException("produto indisponivel"));
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit"
        );

        assertThat(cadastro.emailPrincipal()).isEqualTo("ana@eickrono.com");
        verify(clienteAdministracaoCadastroKeycloak).criarUsuarioPendente("Ana Souza", "ana@eickrono.com", "SenhaForte@123");
    }

    @Test
    @DisplayName("deve cancelar cadastro pendente publico removendo o usuário pendente do Keycloak")
    void deveCancelarCadastroPendentePublico() {
        AtomicReference<CadastroConta> salvo = new AtomicReference<>();
        when(disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com")).thenReturn(Optional.empty());
        when(clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                "Ana Souza", "ana@eickrono.com", "SenhaForte@123"))
                .thenReturn(new CadastroKeycloakProvisionado("sub-ana", "ana@eickrono.com", "Ana Souza"));
        when(cadastroContaRepositorio.save(any(CadastroConta.class))).thenAnswer(invocation -> {
            CadastroConta cadastro = invocation.getArgument(0);
            salvo.set(cadastro);
            return cadastro;
        });

        CadastroInternoRealizado cadastro = servicoPublico.cadastrarPublico(
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte@123",
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit"
        );
        when(cadastroContaRepositorio.findByCadastroId(cadastro.cadastroId())).thenReturn(Optional.of(salvo.get()));

        servicoPublico.cancelarCadastroPendentePublico(cadastro.cadastroId());

        verify(clienteAdministracaoCadastroKeycloak).removerUsuarioPendente("sub-ana");
        verify(cadastroContaRepositorio).delete(salvo.get());
    }

    @Test
    @DisplayName("deve resolver contexto central por sub sem consultar o produto")
    void deveResolverContextoCentralPorSubSemConsultarProduto() {
        CadastroConta cadastro = new CadastroConta(
                java.util.UUID.randomUUID(),
                "sub-ana",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T18:00:00Z"),
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T09:00:00Z")
        );
        cadastro.marcarEmailConfirmado(OffsetDateTime.parse("2026-03-16T10:00:00Z"));
        cadastro.definirPessoaIdPerfil(10L, OffsetDateTime.parse("2026-03-16T10:01:00Z"));
        when(cadastroContaRepositorio.findBySubjectRemoto("sub-ana")).thenReturn(Optional.of(cadastro));

        Optional<ContextoPessoaPerfilSistema> resultado =
                servicoPublico.buscarContextoCentralPorSubPublico("sub-ana");

        assertThat(resultado).isPresent();
        assertThat(resultado.orElseThrow().usuario()).isEqualTo("ana.souza");
        verify(clienteContextoPessoaPerfilSistema, never()).buscarPorSub(any());
    }

    @Test
    @DisplayName("nao deve resolver contexto central por sub quando cadastro nao tem usuario")
    void naoDeveResolverContextoCentralPorSubSemUsuarioMaterializado() throws Exception {
        CadastroConta cadastro = new CadastroConta(
                java.util.UUID.randomUUID(),
                "sub-sem-usuario",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T18:00:00Z"),
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T09:00:00Z")
        );
        simularUsuarioLegadoNulo(cadastro);
        cadastro.marcarEmailConfirmado(OffsetDateTime.parse("2026-03-16T10:00:00Z"));
        cadastro.definirPessoaIdPerfil(10L, OffsetDateTime.parse("2026-03-16T10:01:00Z"));
        when(cadastroContaRepositorio.findBySubjectRemoto("sub-sem-usuario")).thenReturn(Optional.of(cadastro));

        Optional<ContextoPessoaPerfilSistema> resultado =
                servicoPublico.buscarContextoCentralPorSubPublico("sub-sem-usuario");

        assertThat(resultado).isEmpty();
        verify(clienteContextoPessoaPerfilSistema, never()).buscarPorSub(any());
    }

    @Test
    @DisplayName("nao deve resolver contexto central por email quando cadastro nao tem usuario")
    void naoDeveResolverContextoCentralPorEmailSemUsuarioMaterializado() throws Exception {
        CadastroConta cadastro = new CadastroConta(
                java.util.UUID.randomUUID(),
                "sub-sem-usuario",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T18:00:00Z"),
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T09:00:00Z")
        );
        simularUsuarioLegadoNulo(cadastro);
        cadastro.marcarEmailConfirmado(OffsetDateTime.parse("2026-03-16T10:00:00Z"));
        cadastro.definirPessoaIdPerfil(10L, OffsetDateTime.parse("2026-03-16T10:01:00Z"));
        when(cadastroContaRepositorio.findByEmailPrincipal("ana@eickrono.com")).thenReturn(Optional.of(cadastro));

        Optional<ContextoPessoaPerfilSistema> resultado =
                servicoPublico.buscarContextoCentralPorEmailPublico("ANA@EICKRONO.COM");

        assertThat(resultado).isEmpty();
    }

    private static void simularUsuarioLegadoNulo(final CadastroConta cadastro) throws Exception {
        Field campoUsuario = CadastroConta.class.getDeclaredField("usuario");
        campoUsuario.setAccessible(true);
        campoUsuario.set(cadastro, null);
    }

    @Test
    @DisplayName("deve expurgar automaticamente cadastros pendentes com mais de 48 horas")
    void deveExpurgarCadastrosPendentesExpirados() {
        CadastroConta expirado = new CadastroConta(
                java.util.UUID.randomUUID(),
                "sub-expirado",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T18:00:00Z"),
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                OffsetDateTime.parse("2026-03-16T09:00:00Z")
        );
        assertThat(expirado.getStatus()).isEqualTo(StatusCadastroConta.PENDENTE_EMAIL);
        when(cadastroContaRepositorio.findByStatusAndCriadoEmBefore(
                eq(StatusCadastroConta.PENDENTE_EMAIL),
                eq(OffsetDateTime.parse("2026-03-17T10:00:00Z"))))
                .thenReturn(List.of(expirado));

        int removidos = servicoPublico.expurgarCadastrosPendentesExpirados();

        assertThat(removidos).isEqualTo(1);
        verify(clienteAdministracaoCadastroKeycloak).removerUsuarioPendente("sub-expirado");
        verify(cadastroContaRepositorio).delete(expirado);
    }
}
