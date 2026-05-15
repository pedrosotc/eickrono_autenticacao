package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoCodigoRecuperacaoSenhaRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroPublicoRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.PerfilSistemaProjetoPorEmailResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.RecuperacaoSenhaIniciada;
import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.aplicacao.servico.AvaliacaoSegurancaAplicativoService;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.ContextoSocialPendenteJdbc;
import com.eickrono.api.identidade.aplicacao.servico.LocalizadorPerfilSistemaProjetoPorEmailJdbc;
import com.eickrono.api.identidade.aplicacao.servico.RecuperacaoSenhaService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorContextoAutenticacaoService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorProjetoFluxoPublico;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.servico.ResultadoValidacaoTokenDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.TokenDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.VinculoSocialService;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CadastroApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CadastroApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmacaoCodigoRecuperacaoSenhaApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmacaoEmailCadastroApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmarCodigoRecuperacaoSenhaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmarEmailCadastroApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CriarSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CriarSessaoSocialApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DisponibilidadeUsuarioCadastroApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.IniciarRecuperacaoSenhaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RecuperacaoSenhaApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RenovarSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RedefinirSenhaRecuperacaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.SessaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.VinculoSocialPendenteApiRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/publica")
public class FluxoPublicoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FluxoPublicoController.class);

    private static final String ERRO_KEYCLOAK_CONTA_DESABILITADA = "Account disabled";
    private static final String ERRO_KEYCLOAK_CONTA_INCOMPLETA = "Account is not fully set up";
    private static final String ERRO_KEYCLOAK_CREDENCIAIS_INVALIDAS = "Invalid user credentials";
    private static final String STATUS_PENDENTE_EMAIL = "PENDENTE_EMAIL";
    private static final String STATUS_LIBERADO = "LIBERADO";
    private static final String STATUS_PENDENTE_LIBERACAO_PRODUTO = "PENDENTE_LIBERACAO_PRODUTO";
    private static final String PROXIMO_PASSO_LOGIN = "LOGIN";

    private final CadastroContaInternaServico cadastroContaInternaServico;
    private final AtestacaoAppServico atestacaoAppServico;
    private final AvaliacaoSegurancaAplicativoService avaliacaoSegurancaAplicativoService;
    private final AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;
    private final ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService;
    private final ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;
    private final ContextoSocialPendenteJdbc contextoSocialPendenteJdbc;
    private final LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail;
    private final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final RegistroDispositivoService registroDispositivoService;
    private final RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;
    private final TokenDispositivoService tokenDispositivoService;
    private final VinculoSocialService vinculoSocialService;
    private final JwtDecoder jwtDecoder;

    public FluxoPublicoController(final CadastroContaInternaServico cadastroContaInternaServico,
                                  final AtestacaoAppServico atestacaoAppServico,
                                  final AvaliacaoSegurancaAplicativoService avaliacaoSegurancaAplicativoService,
                                  final AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico,
                                  final ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService,
                                  final ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak,
                                  final ContextoSocialPendenteJdbc contextoSocialPendenteJdbc,
                                  final LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail,
                                  final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico,
                                  final RecuperacaoSenhaService recuperacaoSenhaService,
                                  final RegistroDispositivoService registroDispositivoService,
                                  final RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService,
                                  final TokenDispositivoService tokenDispositivoService,
                                  final VinculoSocialService vinculoSocialService,
                                  final JwtDecoder jwtDecoder) {
        this.cadastroContaInternaServico = Objects.requireNonNull(
                cadastroContaInternaServico, "cadastroContaInternaServico é obrigatório");
        this.atestacaoAppServico = Objects.requireNonNull(atestacaoAppServico, "atestacaoAppServico é obrigatório");
        this.avaliacaoSegurancaAplicativoService = Objects.requireNonNull(
                avaliacaoSegurancaAplicativoService, "avaliacaoSegurancaAplicativoService é obrigatório");
        this.autenticacaoSessaoInternaServico = Objects.requireNonNull(
                autenticacaoSessaoInternaServico, "autenticacaoSessaoInternaServico é obrigatório");
        this.resolvedorContextoAutenticacaoService = Objects.requireNonNull(
                resolvedorContextoAutenticacaoService, "resolvedorContextoAutenticacaoService é obrigatório");
        this.clienteAdministracaoVinculosSociaisKeycloak = Objects.requireNonNull(
                clienteAdministracaoVinculosSociaisKeycloak, "clienteAdministracaoVinculosSociaisKeycloak é obrigatório");
        this.contextoSocialPendenteJdbc = Objects.requireNonNull(
                contextoSocialPendenteJdbc, "contextoSocialPendenteJdbc é obrigatório");
        this.localizadorPerfilSistemaProjetoPorEmail = Objects.requireNonNull(
                localizadorPerfilSistemaProjetoPorEmail, "localizadorPerfilSistemaProjetoPorEmail é obrigatório");
        this.resolvedorProjetoFluxoPublico = Objects.requireNonNull(
                resolvedorProjetoFluxoPublico, "resolvedorProjetoFluxoPublico é obrigatório");
        this.recuperacaoSenhaService = Objects.requireNonNull(
                recuperacaoSenhaService, "recuperacaoSenhaService é obrigatório");
        this.registroDispositivoService = Objects.requireNonNull(
                registroDispositivoService, "registroDispositivoService é obrigatório");
        this.registroDispositivoLoginSilenciosoService = Objects.requireNonNull(
                registroDispositivoLoginSilenciosoService, "registroDispositivoLoginSilenciosoService é obrigatório");
        this.tokenDispositivoService = Objects.requireNonNull(
                tokenDispositivoService, "tokenDispositivoService é obrigatório");
        this.vinculoSocialService = Objects.requireNonNull(
                vinculoSocialService, "vinculoSocialService é obrigatório");
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder é obrigatório");
    }

    @PostMapping("/cadastros")
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroApiResposta criarCadastro(@Valid @RequestBody final CadastroApiRequest requisicao,
                                             final HttpServletRequest servletRequest) {
        validarRegrasCadastro(requisicao);
        atestacaoAppServico.validarComprovante(requisicao.atestacao().paraEntrada());
        avaliacaoSegurancaAplicativoService.avaliar(
                "CADASTRO",
                requisicao.aplicacaoId(),
                requisicao.plataformaApp().name(),
                requisicao.segurancaAplicativo(),
                requisicao.emailPrincipal()
        );
        CadastroInternoRealizado cadastro = cadastroContaInternaServico.cadastrarPublico(
                requisicao.tipoPessoa(),
                requisicao.nomeCompleto(),
                requisicao.nomeFantasia(),
                requisicao.usuario(),
                requisicao.sexo(),
                requisicao.paisNascimento(),
                requisicao.dataNascimento(),
                requisicao.emailPrincipal(),
                requisicao.telefone(),
                requisicao.tipoValidacaoTelefone(),
                requisicao.senha(),
                requisicao.aplicacaoId(),
                extrairIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
        atualizarContextosSociaisPendentesDoCadastro(requisicao);
        return new CadastroApiResposta(
                cadastro.cadastroId().toString(),
                "",
                STATUS_PENDENTE_EMAIL,
                cadastro.emailPrincipal(),
                Objects.requireNonNullElse(requisicao.telefone(), ""),
                cadastro.verificacaoEmailObrigatoria(),
                "VALIDAR_CONTATOS"
        );
    }

    @GetMapping("/cadastros/usuarios/disponibilidade")
    public DisponibilidadeUsuarioCadastroApiResposta consultarDisponibilidadeUsuario(
            @RequestParam final String usuario,
            @RequestParam(required = false) final String aplicacaoId) {
        String usuarioNormalizado = Objects.requireNonNull(usuario, "usuario é obrigatório")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (usuarioNormalizado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usuario é obrigatório.");
        }
        return new DisponibilidadeUsuarioCadastroApiResposta(
                usuarioNormalizado,
                aplicacaoId == null || aplicacaoId.isBlank()
                        ? cadastroContaInternaServico.identificadorPublicoSistemaDisponivelPublico(usuarioNormalizado)
                        : cadastroContaInternaServico.identificadorPublicoSistemaDisponivelPublico(
                                usuarioNormalizado,
                                aplicacaoId
                        )
        );
    }

    @PostMapping("/cadastros/{cadastroId}/confirmacoes/email")
    public ConfirmacaoEmailCadastroApiResposta confirmarEmailCadastro(@PathVariable final String cadastroId,
                                                                     @Valid @RequestBody
                                                                     final ConfirmarEmailCadastroApiRequest requisicao) {
        ConfirmacaoEmailCadastroPublicoRealizada confirmacao = cadastroContaInternaServico.confirmarEmailPublico(
                parseCadastroId(cadastroId),
                requisicao.codigo()
        );
        return new ConfirmacaoEmailCadastroApiResposta(
                confirmacao.cadastroId().toString(),
                confirmacao.perfilSistemaId(),
                confirmacao.statusPerfilSistema(),
                confirmacao.emailPrincipal(),
                confirmacao.emailConfirmado(),
                confirmacao.podeAutenticar(),
                confirmacao.proximoPasso().isBlank()
                        ? PROXIMO_PASSO_LOGIN
                        : confirmacao.proximoPasso()
        );
    }

    @PostMapping("/cadastros/{cadastroId}/confirmacoes/email/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarConfirmacaoEmailCadastro(@PathVariable final String cadastroId) {
        cadastroContaInternaServico.reenviarCodigoEmail(parseCadastroId(cadastroId));
    }

    @DeleteMapping("/cadastros/{cadastroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCadastro(@PathVariable final String cadastroId) {
        cadastroContaInternaServico.cancelarCadastroPendentePublico(parseCadastroId(cadastroId));
    }

    @DeleteMapping("/sessoes/contextos-sociais-pendentes/{contextoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarContextoSocialPendente(@PathVariable final UUID contextoId,
                                               @RequestParam final String aplicacaoId) {
        ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(aplicacaoId);
        contextoSocialPendenteJdbc.cancelar(
                contextoId,
                projeto.clienteEcossistemaId(),
                "USUARIO_DESISTIU"
        );
    }

    @PostMapping("/sessoes")
    public SessaoApiResposta criarSessao(@Valid @RequestBody final CriarSessaoApiRequest requisicao,
                                         final HttpServletRequest servletRequest) {
        String loginNormalizado = requisicao.login().trim().toLowerCase(Locale.ROOT);
        String loginMascarado = mascararIdentificador(loginNormalizado);
        String instalacaoMascarada = mascararIdentificador(requisicao.dispositivo().identificadorInstalacao());
        String identificadorAplicativo = resolverIdentificadorAplicativo(requisicao);
        Optional<ContextoSocialPendenteJdbc.ContextoSocialPendenteAtivo> contextoSocialPendente =
                resolverContextoSocialPendenteAtivo(requisicao, loginNormalizado);
        LOGGER.info(
                "login_publico_recebido login={} aplicacaoId={} plataforma={} instalacao={} identificadorAplicativo={} ip={}",
                loginMascarado,
                requisicao.aplicacaoId(),
                requisicao.dispositivo().plataforma(),
                instalacaoMascarada,
                identificadorAplicativo,
                extrairIp(servletRequest)
        );

        try {
            atestacaoAppServico.validarComprovante(requisicao.atestacao().paraEntrada());
            LOGGER.info(
                    "login_publico_atestacao_validada login={} provedor={} tipoComprovante={}",
                    loginMascarado,
                    requisicao.atestacao().provedor(),
                    requisicao.atestacao().tipoComprovante()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "login_publico_atestacao_rejeitada login={} provedor={} tipoComprovante={} motivo={}",
                    loginMascarado,
                    requisicao.atestacao().provedor(),
                    requisicao.atestacao().tipoComprovante(),
                    exception.getMessage()
            );
            throw exception;
        }
        try {
            avaliacaoSegurancaAplicativoService.avaliar(
                    "LOGIN",
                    requisicao.aplicacaoId(),
                    requisicao.dispositivo().plataforma(),
                    requisicao.segurancaAplicativo(),
                    loginNormalizado
            );
            LOGGER.info(
                    "login_publico_seguranca_aprovada login={} provedorAtestacao={} scoreRiscoLocal={} assinaturaValida={} identidadeAplicativoValida={} sinaisRisco={}",
                    loginMascarado,
                    requisicao.segurancaAplicativo().provedorAtestacao(),
                    requisicao.segurancaAplicativo().scoreRiscoLocal(),
                    requisicao.segurancaAplicativo().assinaturaValida(),
                    requisicao.segurancaAplicativo().identidadeAplicativoValida(),
                    requisicao.segurancaAplicativo().sinaisRisco() == null ? 0 : requisicao.segurancaAplicativo().sinaisRisco().size()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "login_publico_seguranca_rejeitada login={} provedorAtestacao={} scoreRiscoLocal={} motivo={}",
                    loginMascarado,
                    requisicao.segurancaAplicativo().provedorAtestacao(),
                    requisicao.segurancaAplicativo().scoreRiscoLocal(),
                    exception.getMessage()
            );
            throw exception;
        }
        SessaoInternaAutenticada sessao;
        try {
            sessao = autenticacaoSessaoInternaServico.autenticar(
                    loginNormalizado,
                    requisicao.senha()
            );
        } catch (ResponseStatusException exception) {
            FluxoPublicoException erroAssistido = tratarFalhaLoginAssistido(
                    contextoSocialPendente,
                    exception
            );
            if (erroAssistido != null) {
                LOGGER.warn(
                        "login_publico_vinculacao_pendente_rejeitada login={} codigo={} status={} motivo={}",
                        loginMascarado,
                        erroAssistido.getCodigo(),
                        erroAssistido.getStatus().value(),
                        erroAssistido.getMessage()
                );
                throw erroAssistido;
            }
            FluxoPublicoException erroMapeado = mapearErroLoginPublico(loginNormalizado, exception);
            LOGGER.warn(
                    "login_publico_autenticacao_rejeitada login={} codigo={} status={} motivo={}",
                    loginMascarado,
                    erroMapeado.getCodigo(),
                    erroMapeado.getStatus().value(),
                    Objects.requireNonNullElse(exception.getReason(), "")
            );
            throw erroMapeado;
        }
        LOGGER.info(
                "login_publico_credenciais_validadas login={} autenticado={} expiresIn={}",
                loginMascarado,
                sessao.autenticado(),
                sessao.expiresIn()
        );
        ContextoPessoaPerfilSistema contexto = resolvedorContextoAutenticacaoService
                .buscarPorEmailPublico(loginNormalizado)
                .orElseThrow(() -> {
                    LOGGER.warn(
                            "login_publico_contexto_ausente login={} motivo=conta_nao_liberada",
                            loginMascarado
                    );
                    return new FluxoPublicoException(
                            HttpStatus.FORBIDDEN,
                            "conta_nao_liberada",
                            "A conta ainda não está liberada para utilizar o aplicativo."
                    );
                });
        String statusPerfilSistema = Objects.requireNonNullElse(contexto.statusPerfilSistema(), STATUS_LIBERADO);
        if (!statusPerfilSistemaPermiteLoginCentral(statusPerfilSistema)) {
            LOGGER.warn(
                    "login_publico_contexto_bloqueado login={} statusPerfilSistema={}",
                    loginMascarado,
                    statusPerfilSistema
            );
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_nao_liberada",
                    "A conta ainda não está liberada para utilizar o aplicativo."
            );
        }
        if (contextoSocialPendente.isPresent()) {
            vinculoSocialService.vincularContextoPendenteAposLoginLocal(
                    decodificarSessaoCentral(sessao.accessToken()),
                    contextoSocialPendente.orElseThrow(),
                    requisicao.aplicacaoId()
            );
        }
        return concluirSessaoPublica(
                sessao,
                contexto,
                requisicao.dispositivo(),
                statusPerfilSistema,
                loginMascarado
        );
    }

    private Optional<ContextoSocialPendenteJdbc.ContextoSocialPendenteAtivo> resolverContextoSocialPendenteAtivo(
            final CriarSessaoApiRequest requisicao,
            final String loginNormalizado) {
        if (requisicao.contextoSocialPendenteId() == null) {
            return Optional.empty();
        }
        ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(requisicao.aplicacaoId());
        ContextoSocialPendenteJdbc.ContextoSocialPendenteAtivo contexto = contextoSocialPendenteJdbc
                .buscarAtivo(requisicao.contextoSocialPendenteId(), projeto.clienteEcossistemaId())
                .orElseThrow(() -> erroVinculacaoSocialPendenteCancelada(
                        "A vinculacao social pendente nao esta mais disponivel. Tente autenticar novamente pela rede social."
                ));
        if (!contexto.modoEntrarEVincular() || !contexto.aceitaLogin(loginNormalizado)) {
            contextoSocialPendenteJdbc.cancelar(
                    contexto.id(),
                    contexto.clienteEcossistemaId(),
                    "CONTA_DIVERGENTE"
            );
            throw erroVinculacaoSocialPendenteCancelada(
                    "A vinculacao social pendente foi cancelada porque o login informado nao corresponde a conta sugerida."
            );
        }
        return Optional.of(contexto);
    }

    private FluxoPublicoException tratarFalhaLoginAssistido(
            final Optional<ContextoSocialPendenteJdbc.ContextoSocialPendenteAtivo> contextoSocialPendente,
            final ResponseStatusException exception) {
        if (contextoSocialPendente.isEmpty()) {
            return null;
        }
        String motivo = Objects.requireNonNullElse(exception.getReason(), "").trim();
        if (!ERRO_KEYCLOAK_CREDENCIAIS_INVALIDAS.equalsIgnoreCase(motivo)
                && !"Credenciais invalidas.".equalsIgnoreCase(motivo)) {
            return null;
        }
        ContextoSocialPendenteJdbc.ContextoSocialPendenteAtivo contexto = contextoSocialPendente.orElseThrow();
        ContextoSocialPendenteJdbc.ResultadoFalhaEntrarEVincular resultado =
                contextoSocialPendenteJdbc.registrarFalhaEntrarEVincular(
                        contexto.id(),
                        contexto.clienteEcossistemaId()
                );
        if (resultado.canceladoPorLimite()) {
            return erroVinculacaoSocialPendenteCancelada(
                    "A vinculacao social pendente foi cancelada apos 3 tentativas invalidas de autenticacao."
            );
        }
        return null;
    }

    private FluxoPublicoException erroVinculacaoSocialPendenteCancelada(final String mensagem) {
        return new FluxoPublicoException(
                HttpStatus.CONFLICT,
                "vinculacao_social_pendente_cancelada",
                mensagem
        );
    }

    private boolean statusPerfilSistemaPermiteLoginCentral(final String statusPerfilSistema) {
        return STATUS_LIBERADO.equalsIgnoreCase(statusPerfilSistema)
                || STATUS_PENDENTE_LIBERACAO_PRODUTO.equalsIgnoreCase(statusPerfilSistema);
    }

    @PostMapping("/sessoes/sociais")
    public SessaoApiResposta criarSessaoSocial(@Valid @RequestBody final CriarSessaoSocialApiRequest requisicao,
                                               final HttpServletRequest servletRequest) {
        String provedorNormalizado = requisicao.provedor().trim().toLowerCase(Locale.ROOT);
        String instalacaoMascarada = mascararIdentificador(requisicao.dispositivo().identificadorInstalacao());
        String identificadorAplicativo = resolverIdentificadorAplicativo(requisicao);
        LOGGER.info(
                "login_social_publico_recebido provedor={} aplicacaoId={} plataforma={} instalacao={} identificadorAplicativo={} ip={}",
                provedorNormalizado,
                requisicao.aplicacaoId(),
                requisicao.dispositivo().plataforma(),
                instalacaoMascarada,
                identificadorAplicativo,
                extrairIp(servletRequest)
        );

        try {
            atestacaoAppServico.validarComprovante(requisicao.atestacao().paraEntrada());
            LOGGER.info(
                    "login_social_publico_atestacao_validada provedor={} tipoComprovante={}",
                    provedorNormalizado,
                    requisicao.atestacao().tipoComprovante()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "login_social_publico_atestacao_rejeitada provedor={} tipoComprovante={} motivo={}",
                    provedorNormalizado,
                    requisicao.atestacao().tipoComprovante(),
                    exception.getMessage()
            );
            throw exception;
        }

        try {
            avaliacaoSegurancaAplicativoService.avaliar(
                    "LOGIN_SOCIAL",
                    requisicao.aplicacaoId(),
                    requisicao.dispositivo().plataforma(),
                    requisicao.segurancaAplicativo(),
                    provedorNormalizado
            );
            LOGGER.info(
                    "login_social_publico_seguranca_aprovada provedor={} provedorAtestacao={} scoreRiscoLocal={} assinaturaValida={} identidadeAplicativoValida={} sinaisRisco={}",
                    provedorNormalizado,
                    requisicao.segurancaAplicativo().provedorAtestacao(),
                    requisicao.segurancaAplicativo().scoreRiscoLocal(),
                    requisicao.segurancaAplicativo().assinaturaValida(),
                    requisicao.segurancaAplicativo().identidadeAplicativoValida(),
                    requisicao.segurancaAplicativo().sinaisRisco() == null ? 0 : requisicao.segurancaAplicativo().sinaisRisco().size()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "login_social_publico_seguranca_rejeitada provedor={} provedorAtestacao={} scoreRiscoLocal={} motivo={}",
                    provedorNormalizado,
                    requisicao.segurancaAplicativo().provedorAtestacao(),
                    requisicao.segurancaAplicativo().scoreRiscoLocal(),
                    exception.getMessage()
            );
            throw exception;
        }

        SessaoInternaAutenticada sessao = autenticacaoSessaoInternaServico.autenticarSocial(
                provedorNormalizado,
                requisicao.tokenExterno()
        );
        LOGGER.info(
                "login_social_publico_sessao_emitida provedor={} autenticado={} expiresIn={}",
                provedorNormalizado,
                sessao.autenticado(),
                sessao.expiresIn()
        );
        Jwt jwtSessaoCentral = decodificarSessaoCentral(sessao.accessToken());
        Optional<ContextoPessoaPerfilSistema> contextoDireto = buscarContextoParaSessao(jwtSessaoCentral);
        if (contextoDireto.isEmpty()) {
            throw montarErroSocialSemContaLocal(jwtSessaoCentral, requisicao.aplicacaoId());
        }
        String statusPerfilSistema = Objects.requireNonNullElse(
                contextoDireto.orElseThrow().statusPerfilSistema(),
                STATUS_LIBERADO
        );
        if (!statusPerfilSistemaPermiteLoginCentral(statusPerfilSistema)) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_nao_liberada",
                    "A conta ainda não está liberada para utilizar o aplicativo."
            );
        }
        return concluirSessaoPublica(
                sessao,
                contextoDireto.orElseThrow(),
                requisicao.dispositivo(),
                statusPerfilSistema,
                provedorNormalizado
        );
    }

    @PostMapping("/sessoes/refresh")
    public SessaoApiResposta renovarSessao(@Valid @RequestBody final RenovarSessaoApiRequest requisicao) {
        LOGGER.debug(
                "refresh_publico_recebido tokenDispositivoInformado={} aplicacaoId={} dispositivoInformado={}",
                StringUtils.hasText(requisicao.tokenDispositivo()),
                requisicao.aplicacaoId(),
                requisicao.dispositivo() != null
        );
        SessaoInternaAutenticada sessao = autenticacaoSessaoInternaServico.renovar(
                requisicao.refreshToken(),
                requisicao.tokenDispositivo()
        );
        if (!StringUtils.hasText(requisicao.tokenDispositivo())
                && requisicao.dispositivo() != null
                && StringUtils.hasText(requisicao.aplicacaoId())) {
            LOGGER.debug("refresh_publico_branch=recompor_sessao_local");
            return recomporSessaoLocalNaRenovacao(sessao, requisicao);
        }
        if (StringUtils.hasText(requisicao.tokenDispositivo())) {
            LOGGER.debug("refresh_publico_branch=renovar_sessao_local_existente");
            return renovarSessaoLocalExistente(sessao, requisicao.tokenDispositivo());
        }
        LOGGER.debug("refresh_publico_branch=sessao_central_sem_dispositivo");
        return new SessaoApiResposta(
                sessao.autenticado(),
                sessao.tipoToken(),
                sessao.accessToken(),
                sessao.refreshToken(),
                sessao.expiresIn(),
                null,
                null,
                null,
                null,
                null,
                null,
                STATUS_LIBERADO,
                null,
                false,
                true,
                true
        );
    }

    private SessaoApiResposta renovarSessaoLocalExistente(final SessaoInternaAutenticada sessao,
                                                          final String tokenDispositivo) {
        Optional<TokenDispositivoService.TokenDispositivoValidado> tokenAtivo = tokenDispositivoService
                .validarTokenAtivoSemUsuario(tokenDispositivo);
        if (tokenAtivo.isPresent()) {
            TokenDispositivoService.TokenDispositivoValidado tokenValidado = tokenAtivo.orElseThrow();
            ContextoPessoaPerfilSistema contexto = buscarContextoParaSessao(tokenValidado.usuarioSub()).orElseThrow(() ->
                    new FluxoPublicoException(
                            HttpStatus.FORBIDDEN,
                            "conta_nao_liberada",
                            "A conta ainda não está liberada para utilizar o aplicativo."
                    ));
            String statusPerfilSistema = Objects.requireNonNullElse(contexto.statusPerfilSistema(), STATUS_LIBERADO);
            if (!statusPerfilSistemaPermiteLoginCentral(statusPerfilSistema)) {
                throw new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_nao_liberada",
                        "A conta ainda não está liberada para utilizar o aplicativo."
                );
            }
            LOGGER.debug(
                    "refresh_publico_contexto_local statusPerfilSistema={} emailPrincipal={} tokenValido=true tokenExpiraEm={}",
                    statusPerfilSistema,
                    contexto.emailPrincipal(),
                    tokenValidado.expiraEm()
            );
            return new SessaoApiResposta(
                    sessao.autenticado(),
                    sessao.tipoToken(),
                    sessao.accessToken(),
                    sessao.refreshToken(),
                    sessao.expiresIn(),
                    tokenDispositivo,
                    tokenValidado.expiraEm(),
                    null,
                    null,
                    null,
                    null,
                    statusPerfilSistema,
                    contexto.emailPrincipal(),
                    contexto.usuario(),
                    false,
                    true,
                    true
            );
        }
        Jwt jwtSessaoCentral = decodificarSessaoCentral(sessao.accessToken());
        ContextoPessoaPerfilSistema contexto = buscarContextoParaSessao(jwtSessaoCentral).orElseThrow(() ->
                new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_nao_liberada",
                        "A conta ainda não está liberada para utilizar o aplicativo."
                ));
        String statusPerfilSistema = Objects.requireNonNullElse(contexto.statusPerfilSistema(), STATUS_LIBERADO);
        if (!statusPerfilSistemaPermiteLoginCentral(statusPerfilSistema)) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_nao_liberada",
                    "A conta ainda não está liberada para utilizar o aplicativo."
            );
        }
        ResultadoValidacaoTokenDispositivo validacaoToken = tokenDispositivoService.validarToken(
                jwtSessaoCentral.getSubject(),
                tokenDispositivo
        );
        LOGGER.debug(
                "refresh_publico_contexto_local statusPerfilSistema={} emailPrincipal={} tokenValido={} tokenExpiraEm={}",
                statusPerfilSistema,
                contexto.emailPrincipal(),
                validacaoToken.valido(),
                validacaoToken.expiraEm()
        );
        return new SessaoApiResposta(
                sessao.autenticado(),
                sessao.tipoToken(),
                sessao.accessToken(),
                sessao.refreshToken(),
                sessao.expiresIn(),
                validacaoToken.valido() ? tokenDispositivo : null,
                validacaoToken.valido() ? validacaoToken.expiraEm() : null,
                null,
                null,
                null,
                null,
                statusPerfilSistema,
                contexto.emailPrincipal(),
                contexto.usuario(),
                false,
                true,
                true
        );
    }

    private SessaoApiResposta recomporSessaoLocalNaRenovacao(final SessaoInternaAutenticada sessao,
                                                             final RenovarSessaoApiRequest requisicao) {
        Jwt jwtSessaoCentral = decodificarSessaoCentral(sessao.accessToken());
        Optional<ContextoPessoaPerfilSistema> contextoOpt = buscarContextoParaSessao(jwtSessaoCentral);
        ContextoPessoaPerfilSistema contexto = contextoOpt.orElseThrow(() -> new FluxoPublicoException(
                HttpStatus.FORBIDDEN,
                "conta_nao_liberada",
                "A conta ainda não está liberada para utilizar o aplicativo."
        ));
        String statusPerfilSistema = Objects.requireNonNullElse(contexto.statusPerfilSistema(), STATUS_LIBERADO);
        if (!statusPerfilSistemaPermiteLoginCentral(statusPerfilSistema)) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_nao_liberada",
                    "A conta ainda não está liberada para utilizar o aplicativo."
            );
        }
        return concluirSessaoPublica(
                sessao,
                contexto,
                requisicao.dispositivo(),
                statusPerfilSistema,
                Objects.requireNonNullElse(contexto.emailPrincipal(), "usuario")
        );
    }

    private SessaoApiResposta concluirSessaoPublica(final SessaoInternaAutenticada sessao,
                                                    final ContextoPessoaPerfilSistema contexto,
                                                    final com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest dispositivo,
                                                    final String statusPerfilSistema,
                                                    final String identificadorLog) {
        try {
            DispositivoSessaoRegistrado dispositivoRegistrado = registroDispositivoLoginSilenciosoService.registrar(
                    contexto,
                    dispositivo
            );
            LOGGER.info(
                    "login_publico_sucesso login={} perfilSistemaId={} statusPerfilSistema={} tokenDispositivoEmitido={} tokenDispositivoExpiraEm={}",
                    identificadorLog,
                    contexto.perfilSistemaId(),
                    statusPerfilSistema,
                    dispositivoRegistrado.tokenDispositivo() != null && !dispositivoRegistrado.tokenDispositivo().isBlank(),
                    dispositivoRegistrado.tokenDispositivoExpiraEm()
            );
            return new SessaoApiResposta(
                    sessao.autenticado(),
                    sessao.tipoToken(),
                    sessao.accessToken(),
                    sessao.refreshToken(),
                    sessao.expiresIn(),
                    dispositivoRegistrado.tokenDispositivo(),
                    dispositivoRegistrado.tokenDispositivoExpiraEm(),
                    null,
                    null,
                    null,
                    null,
                    statusPerfilSistema,
                    contexto.emailPrincipal(),
                    contexto.usuario(),
                    false,
                    true,
                    true
            );
        } catch (FluxoPublicoException exception) {
            RegistroDispositivoResponse registroInterativo = abrirRegistroInterativoSeNecessario(
                    exception,
                    contexto,
                    dispositivo
            );
            if (registroInterativo == null) {
                throw exception;
            }
            LOGGER.info(
                    "login_publico_dispositivo_pendente login={} perfilSistemaId={} statusPerfilSistema={} registroDispositivoId={} expiraEm={} canais={}",
                    identificadorLog,
                    contexto.perfilSistemaId(),
                    statusPerfilSistema,
                    registroInterativo.registroId(),
                    registroInterativo.expiraEm(),
                    registroInterativo.canaisConfirmacao()
            );
            return new SessaoApiResposta(
                    sessao.autenticado(),
                    sessao.tipoToken(),
                    sessao.accessToken(),
                    sessao.refreshToken(),
                    sessao.expiresIn(),
                    null,
                    null,
                    registroInterativo.registroId(),
                    registroInterativo.expiraEm(),
                    registroInterativo.status(),
                    registroInterativo.canaisConfirmacao(),
                    statusPerfilSistema,
                    contexto.emailPrincipal(),
                    contexto.usuario(),
                    false,
                    false,
                    false
            );
        }
    }

    private RegistroDispositivoResponse abrirRegistroInterativoSeNecessario(
            final FluxoPublicoException exception,
            final ContextoPessoaPerfilSistema contexto,
            final com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest dispositivo) {
        if (!"dispositivo_nao_liberado".equalsIgnoreCase(exception.getCodigo())) {
            return null;
        }
        return registroDispositivoService.solicitarRegistroParaSessao(contexto, dispositivo);
    }

    private Jwt decodificarSessaoCentral(final String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nao foi possivel recompor a sessao local durante a renovacao."
            );
        }
        try {
            return jwtDecoder.decode(accessToken);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nao foi possivel recompor a sessao local durante a renovacao.",
                    exception
            );
        }
    }

    private Optional<ContextoPessoaPerfilSistema> buscarContextoParaSessao(final Jwt jwtSessaoCentral) {
        Optional<ContextoPessoaPerfilSistema> contextoPorSub = Optional.ofNullable(jwtSessaoCentral.getSubject())
                .flatMap(resolvedorContextoAutenticacaoService::buscarPorSubPublico);
        if (contextoPorSub.isPresent()) {
            return contextoPorSub;
        }
        String email = jwtSessaoCentral.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return resolvedorContextoAutenticacaoService.buscarPorEmailPublico(email);
    }

    private Optional<ContextoPessoaPerfilSistema> buscarContextoParaSessao(final String usuarioSub) {
        if (!StringUtils.hasText(usuarioSub)) {
            return Optional.empty();
        }
        return resolvedorContextoAutenticacaoService.buscarPorSubPublico(usuarioSub);
    }

    private FluxoPublicoException montarErroSocialSemContaLocal(final Jwt jwt,
                                                                final String aplicacaoId) {
        Optional<String> emailSocial = extrairEmail(jwt);
        Optional<PerfilSistemaProjetoPorEmailResolvido> contaExistente =
                resolverPerfilSistemaNoProjetoAtual(aplicacaoId, emailSocial);
        Map<String, Object> detalhes = new java.util.LinkedHashMap<>();
        detalhes.put("sub", jwt == null ? null : jwt.getSubject());
        emailSocial.ifPresent(email -> detalhes.put("email", email));
        try {
            ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(aplicacaoId);
            listarIdentidadesFederadasSeguras(jwt == null ? null : jwt.getSubject()).stream()
                    .findFirst()
                    .ifPresent(identidade -> {
                        detalhes.put("provedor", identidade.provedor().getAliasApi());
                        detalhes.put("identificadorExterno", identidade.identificadorExterno());
                        if (StringUtils.hasText(identidade.nomeUsuarioExterno())) {
                            detalhes.put("nomeUsuarioExterno", identidade.nomeUsuarioExterno());
                        }
                        String nomeExibicao = normalizarTexto(
                                identidade.nomeExibicaoExterno(),
                                jwt == null ? null : jwt.getClaimAsString("name"));
                        String urlAvatarExterno = normalizarTexto(
                                identidade.urlAvatarExterno(),
                                jwt == null ? null : jwt.getClaimAsString("picture"),
                                jwt == null ? null : jwt.getClaimAsString("avatar_url"),
                                jwt == null ? null : jwt.getClaimAsString("avatar"));
                        if (StringUtils.hasText(nomeExibicao)) {
                            detalhes.put("nomeExibicaoExterno", nomeExibicao);
                        }
                        if (StringUtils.hasText(urlAvatarExterno)) {
                            detalhes.put("urlAvatarExterno", urlAvatarExterno);
                        }
                        UUID contextoSocialPendenteId = contextoSocialPendenteJdbc.registrarOuAtualizar(
                                projeto,
                                identidade.provedor().getAliasApi(),
                                identidade.identificadorExterno(),
                                emailSocial.orElse(null),
                                identidade.nomeUsuarioExterno(),
                                nomeExibicao,
                                urlAvatarExterno,
                                contaExistente.map(PerfilSistemaProjetoPorEmailResolvido::perfilSistemaId).orElse(null),
                                contaExistente.map(PerfilSistemaProjetoPorEmailResolvido::identificadorPublicoSistemaSugerido).orElse(null)
                        );
                        detalhes.put("contextoSocialPendenteId", contextoSocialPendenteId);
                    });
        } catch (RuntimeException ignored) {
            // Mantém o contrato mínimo quando a infraestrutura complementar falhar.
        }
        if (contaExistente.isPresent()) {
            detalhes.put("acaoSugerida", "ENTRAR_E_VINCULAR");
            detalhes.put("emailContaExistente", contaExistente.get().emailNormalizado());
            detalhes.put("loginSugerido", contaExistente.get().identificadorPublicoSistemaSugerido());
            return new FluxoPublicoException(
                    HttpStatus.CONFLICT,
                    "social_sem_conta_local",
                    "Ja existe uma conta neste projeto com o mesmo e-mail desta rede social. Deseja entrar e vincular agora?",
                    detalhes
            );
        }
        detalhes.put("acaoSugerida", "ABRIR_CADASTRO");
        return new FluxoPublicoException(
                HttpStatus.CONFLICT,
                "social_sem_conta_local",
                "Esta rede social foi autenticada com sucesso, mas ainda nao esta ligada a uma conta local. Deseja abrir o cadastro com os dados recebidos?",
                detalhes
        );
    }

    private Optional<String> extrairEmail(final Jwt jwt) {
        return Optional.ofNullable(jwt)
                .map(token -> token.getClaimAsString("email"))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .map(valor -> valor.toLowerCase(Locale.ROOT));
    }

    private Optional<PerfilSistemaProjetoPorEmailResolvido> resolverPerfilSistemaNoProjetoAtual(
            final String aplicacaoId,
            final Optional<String> emailSocial) {
        if (!StringUtils.hasText(aplicacaoId) || emailSocial.isEmpty()) {
            return Optional.empty();
        }
        try {
            ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(aplicacaoId);
            return localizadorPerfilSistemaProjetoPorEmail.localizar(
                    projeto.clienteEcossistemaId(),
                    emailSocial.get());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String normalizarTexto(final String... valores) {
        for (String valor : valores) {
            if (StringUtils.hasText(valor)) {
                return valor.trim();
            }
        }
        return null;
    }

    private java.util.List<IdentidadeFederadaKeycloak> listarIdentidadesFederadasSeguras(final String usuarioSub) {
        if (!StringUtils.hasText(usuarioSub)) {
            return java.util.List.of();
        }
        try {
            return clienteAdministracaoVinculosSociaisKeycloak.listarIdentidadesFederadas(usuarioSub);
        } catch (RuntimeException ignored) {
            return java.util.List.of();
        }
    }

    @PostMapping("/recuperacoes-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecuperacaoSenhaApiResposta iniciarRecuperacaoSenha(
            @Valid @RequestBody final IniciarRecuperacaoSenhaApiRequest requisicao) {
        RecuperacaoSenhaIniciada recuperacao = recuperacaoSenhaService.iniciar(requisicao.emailPrincipal());
        return new RecuperacaoSenhaApiResposta(
                recuperacao.fluxoId().toString(),
                "Se este e-mail estiver cadastrado, enviaremos um código de verificação."
        );
    }

    @PostMapping("/recuperacoes-senha/{fluxoId}/confirmacoes/email")
    public ConfirmacaoCodigoRecuperacaoSenhaApiResposta confirmarCodigoRecuperacaoSenha(
            @PathVariable final String fluxoId,
            @Valid @RequestBody final ConfirmarCodigoRecuperacaoSenhaApiRequest requisicao) {
        ConfirmacaoCodigoRecuperacaoSenhaRealizada confirmacao =
                recuperacaoSenhaService.confirmarCodigo(parseCadastroId(fluxoId), requisicao.codigo());
        return new ConfirmacaoCodigoRecuperacaoSenhaApiResposta(
                confirmacao.fluxoId().toString(),
                confirmacao.codigoConfirmado(),
                confirmacao.podeDefinirSenha()
        );
    }

    @PostMapping("/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarCodigoRecuperacaoSenha(@PathVariable final String fluxoId) {
        recuperacaoSenhaService.reenviarCodigo(parseCadastroId(fluxoId));
    }

    @PostMapping("/recuperacoes-senha/{fluxoId}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenhaRecuperacaoSenha(
            @PathVariable final String fluxoId,
            @Valid @RequestBody final RedefinirSenhaRecuperacaoApiRequest requisicao) {
        recuperacaoSenhaService.redefinirSenha(
                parseCadastroId(fluxoId),
                requisicao.senha(),
                requisicao.confirmacaoSenha()
        );
    }

    private static void validarRegrasCadastro(final CadastroApiRequest requisicao) {
        if (!Objects.equals(requisicao.senha(), requisicao.confirmacaoSenha())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A confirmação de senha não confere.");
        }
        if (requisicao.tipoPessoa() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipoPessoa é obrigatório.");
        }
        if (requisicao.tipoPessoa().name().equals("FISICA")) {
            validarFisica(requisicao.sexo(), requisicao.paisNascimento(), requisicao.dataNascimento());
            return;
        }
        if (requisicao.dataNascimento() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Pessoa jurídica não deve informar data de nascimento.");
        }
    }

    private void atualizarContextosSociaisPendentesDoCadastro(final CadastroApiRequest requisicao) {
        List<VinculoSocialPendenteApiRequest> vinculos = resolverVinculosSociaisPendentesDoCadastro(requisicao);
        if (vinculos.isEmpty()) {
            return;
        }
        ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(requisicao.aplicacaoId());
        for (VinculoSocialPendenteApiRequest vinculo : vinculos) {
            contextoSocialPendenteJdbc.registrarOuAtualizar(
                    projeto,
                    vinculo.provedor(),
                    vinculo.identificadorExterno(),
                    StringUtils.hasText(vinculo.email()) ? vinculo.email() : requisicao.emailPrincipal(),
                    vinculo.nomeUsuarioExterno(),
                    vinculo.nomeCompleto(),
                    vinculo.urlAvatarExterno(),
                    null,
                    null
            );
        }
    }

    private List<VinculoSocialPendenteApiRequest> resolverVinculosSociaisPendentesDoCadastro(
            final CadastroApiRequest requisicao) {
        LinkedHashMap<String, VinculoSocialPendenteApiRequest> vinculos = new LinkedHashMap<>();
        adicionarVinculoSocialPendenteCadastro(vinculos, requisicao.vinculoSocialPendente());
        if (requisicao.vinculosSociaisPendentes() != null) {
            for (VinculoSocialPendenteApiRequest vinculo : requisicao.vinculosSociaisPendentes()) {
                adicionarVinculoSocialPendenteCadastro(vinculos, vinculo);
            }
        }
        return List.copyOf(vinculos.values());
    }

    private static void adicionarVinculoSocialPendenteCadastro(
            final LinkedHashMap<String, VinculoSocialPendenteApiRequest> vinculos,
            final VinculoSocialPendenteApiRequest vinculo) {
        if (vinculo == null
                || !StringUtils.hasText(vinculo.provedor())
                || !StringUtils.hasText(vinculo.identificadorExterno())) {
            return;
        }
        vinculos.put(
                vinculo.provedor().trim().toLowerCase(Locale.ROOT)
                        + "::"
                        + vinculo.identificadorExterno().trim(),
                vinculo
        );
    }

    private static void validarFisica(final Object sexo, final String paisNascimento, final LocalDate dataNascimento) {
        if (sexo == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "sexo é obrigatório para pessoa física.");
        }
        if (paisNascimento == null || paisNascimento.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "paisNascimento é obrigatório para pessoa física."
            );
        }
        if (dataNascimento == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "dataNascimento é obrigatória para pessoa física."
            );
        }
    }

    private static UUID parseCadastroId(final String cadastroId) {
        try {
            return UUID.fromString(Objects.requireNonNull(cadastroId, "cadastroId é obrigatório"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cadastroId inválido.");
        }
    }

    private FluxoPublicoException mapearErroLoginPublico(final String loginNormalizado,
                                                         final ResponseStatusException exception) {
        String motivo = Objects.requireNonNullElse(exception.getReason(), "").trim();
        if (ERRO_KEYCLOAK_CONTA_DESABILITADA.equalsIgnoreCase(motivo)
                || ERRO_KEYCLOAK_CONTA_INCOMPLETA.equalsIgnoreCase(motivo)) {
            UUID cadastroPendenteId = cadastroContaInternaServico
                    .buscarCadastroPendenteEmailPublico(loginNormalizado)
                    .orElse(null);
            if (cadastroPendenteId != null) {
                return new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_nao_liberada",
                        "A conta ainda não está liberada para utilizar o aplicativo.",
                        Map.of("cadastroId", cadastroPendenteId.toString())
                );
            }
            if (ERRO_KEYCLOAK_CONTA_INCOMPLETA.equalsIgnoreCase(motivo)) {
                return new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_incompleta",
                        "A conta nao esta completamente configurada para autenticacao."
                );
            }
            return new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_desabilitada",
                    "A conta está desabilitada para autenticação."
            );
        }
        if (ERRO_KEYCLOAK_CREDENCIAIS_INVALIDAS.equalsIgnoreCase(motivo)
                || "Credenciais invalidas.".equalsIgnoreCase(motivo)) {
            return new FluxoPublicoException(
                    HttpStatus.UNAUTHORIZED,
                    "credenciais_invalidas",
                    "Credenciais inválidas."
            );
        }
        return new FluxoPublicoException(
                HttpStatus.BAD_GATEWAY,
                "falha_autenticacao",
                "Não foi possível autenticar a sessão agora."
        );
    }

    private static String extrairIp(final HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return servletRequest.getRemoteAddr();
        }
        return forwardedFor.split(",")[0].trim();
    }

    private static String mascararIdentificador(final String valor) {
        if (valor == null || valor.isBlank()) {
            return "vazio";
        }
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        int indiceArroba = normalizado.indexOf('@');
        if (indiceArroba > 0) {
            return mascararTrecho(normalizado.substring(0, indiceArroba))
                    + "@"
                    + mascararTrecho(normalizado.substring(indiceArroba + 1));
        }
        return mascararTrecho(normalizado);
    }

    private static String mascararTrecho(final String valor) {
        if (valor == null || valor.isBlank()) {
            return "vazio";
        }
        if (valor.length() <= 2) {
            return "*".repeat(valor.length());
        }
        return valor.charAt(0) + "***" + valor.charAt(valor.length() - 1);
    }

    private static String resolverIdentificadorAplicativo(final CriarSessaoApiRequest requisicao) {
        return resolverIdentificadorAplicativo(
                requisicao.aplicacaoId(),
                requisicao.segurancaAplicativo().bundleIdentifier(),
                requisicao.segurancaAplicativo().packageName()
        );
    }

    private static String resolverIdentificadorAplicativo(final CriarSessaoSocialApiRequest requisicao) {
        return resolverIdentificadorAplicativo(
                requisicao.aplicacaoId(),
                requisicao.segurancaAplicativo().bundleIdentifier(),
                requisicao.segurancaAplicativo().packageName()
        );
    }

    private static String resolverIdentificadorAplicativo(final String aplicacaoId,
                                                          final String bundleIdentifier,
                                                          final String packageName) {
        if (bundleIdentifier != null && !bundleIdentifier.isBlank()) {
            return bundleIdentifier;
        }
        if (packageName != null && !packageName.isBlank()) {
            return packageName;
        }
        return aplicacaoId;
    }
}
