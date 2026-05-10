package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado;
import com.eickrono.api.identidade.aplicacao.modelo.CadastroKeycloakProvisionado;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroInternoRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroPublicoRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.PessoaCanonicaConfirmada;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro;
import com.eickrono.api.identidade.dominio.modelo.StatusCadastroConta;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import com.eickrono.api.identidade.dominio.repositorio.CadastroContaRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CadastroContaInternaServico {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Logger LOGGER = LoggerFactory.getLogger(CadastroContaInternaServico.class);
    private static final String SISTEMA_PUBLICO_PADRAO = "eickrono-thimisu-app";
    private static final String STATUS_PENDENTE_LIBERACAO_PRODUTO = "PENDENTE_LIBERACAO_PRODUTO";
    private static final String PROXIMO_PASSO_LOGIN = "LOGIN";

    private final CadastroContaRepositorio cadastroContaRepositorio;
    private final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;
    private final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;
    private final ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico;
    private final ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico;
    private final DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService;
    private final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail;
    private final CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail;
    private final DispositivoProperties dispositivoProperties;
    private final Clock clock;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;
    private final RegistradorPendenciaIntegracaoProdutoService registradorPendenciaIntegracaoProdutoService;
    private final ContextoSocialPendenteJdbc contextoSocialPendenteJdbc;
    private final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;
    private ProvisionamentoIdentidadeService provisionamentoIdentidadeServiceCompat;
    private final HexFormat hexFormat = HexFormat.of();

    @Autowired
    public CadastroContaInternaServico(final CadastroContaRepositorio cadastroContaRepositorio,
                                       final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                       final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                       final ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico,
                                       final ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico,
                                       final DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService,
                                       final ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                       final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail,
                                       final CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail,
                                       final DispositivoProperties dispositivoProperties,
                                       final Clock clock,
                                       final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService,
                                       final RegistradorPendenciaIntegracaoProdutoService registradorPendenciaIntegracaoProdutoService,
                                       final ContextoSocialPendenteJdbc contextoSocialPendenteJdbc,
                                       final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico) {
        this(
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
                sincronizacaoModeloMultiappService,
                registradorPendenciaIntegracaoProdutoService,
                contextoSocialPendenteJdbc,
                resolvedorProjetoFluxoPublico,
                true
        );
    }

    public CadastroContaInternaServico(final CadastroContaRepositorio cadastroContaRepositorio,
                                       final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                       final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                       final ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico,
                                       final ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico,
                                       final DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService,
                                       final ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                       final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail,
                                       final CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail,
                                       final DispositivoProperties dispositivoProperties,
                                       final Clock clock,
                                       final RegistradorPendenciaIntegracaoProdutoService registradorPendenciaIntegracaoProdutoService,
                                       final ContextoSocialPendenteJdbc contextoSocialPendenteJdbc,
                                       final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico) {
        this(
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
                null,
                registradorPendenciaIntegracaoProdutoService,
                contextoSocialPendenteJdbc,
                resolvedorProjetoFluxoPublico,
                true
        );
    }

    public CadastroContaInternaServico(final CadastroContaRepositorio cadastroContaRepositorio,
                                       final FormaAcessoRepositorio formaAcessoRepositorio,
                                       final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                       final ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                       final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail,
                                       final DispositivoProperties dispositivoProperties,
                                       final Clock clock) {
        this(
                cadastroContaRepositorio,
                new ClienteContextoPessoaPerfilSistemaLegado(
                        Objects.requireNonNull(formaAcessoRepositorio, "formaAcessoRepositorio é obrigatório"),
                        Objects.requireNonNull(provisionamentoIdentidadeService, "provisionamentoIdentidadeService é obrigatório")),
                clienteAdministracaoCadastroKeycloak,
                null,
                null,
                null,
                provisionamentoIdentidadeService,
                canalEnvioCodigoCadastroEmail,
                email -> {
                },
                dispositivoProperties,
                clock,
                null,
                null,
                null,
                null,
                false
        );
    }

    public CadastroContaInternaServico(final CadastroContaRepositorio cadastroContaRepositorio,
                                       final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                       final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                       final ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico,
                                       final ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico,
                                       final DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService,
                                       final ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                       final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail,
                                       final CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail,
                                       final DispositivoProperties dispositivoProperties,
                                       final Clock clock) {
        this(
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
                null,
                null,
                null,
                null,
                true
        );
    }

    public CadastroContaInternaServico(final CadastroContaRepositorio cadastroContaRepositorio,
                                       final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                       final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                       final ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico,
                                       final ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico,
                                       final DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService,
                                       final ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                       final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail,
                                       final CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail,
                                       final DispositivoProperties dispositivoProperties,
                                       final Clock clock,
                                       final RegistradorPendenciaIntegracaoProdutoService registradorPendenciaIntegracaoProdutoService) {
        this(
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
                null,
                registradorPendenciaIntegracaoProdutoService,
                null,
                null,
                true
        );
    }

    private CadastroContaInternaServico(final CadastroContaRepositorio cadastroContaRepositorio,
                                        final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                        final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                        final ProvisionadorPerfilSistemaServico provisionadorPerfilSistemaServico,
                                        final ConfirmadorPessoaCadastroServico confirmadorPessoaCadastroServico,
                                        final DisponibilidadeUsuarioSistemaService disponibilidadeUsuarioSistemaService,
                                        final ProvisionamentoIdentidadeService provisionamentoIdentidadeServiceCompat,
                                        final CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail,
                                        final CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail,
                                        final DispositivoProperties dispositivoProperties,
                                        final Clock clock,
                                        final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService,
                                        final RegistradorPendenciaIntegracaoProdutoService registradorPendenciaIntegracaoProdutoService,
                                        final ContextoSocialPendenteJdbc contextoSocialPendenteJdbc,
                                        final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico,
                                        final boolean exigirProvisionadorPerfil) {
        this.cadastroContaRepositorio = Objects.requireNonNull(cadastroContaRepositorio, "cadastroContaRepositorio é obrigatório");
        this.clienteContextoPessoaPerfilSistema = Objects.requireNonNull(
                clienteContextoPessoaPerfilSistema, "clienteContextoPessoaPerfilSistema é obrigatório");
        this.clienteAdministracaoCadastroKeycloak = Objects.requireNonNull(
                clienteAdministracaoCadastroKeycloak, "clienteAdministracaoCadastroKeycloak é obrigatório");
        if (exigirProvisionadorPerfil) {
            this.provisionadorPerfilSistemaServico = Objects.requireNonNull(
                    provisionadorPerfilSistemaServico, "provisionadorPerfilSistemaServico é obrigatório");
            this.confirmadorPessoaCadastroServico = Objects.requireNonNull(
                    confirmadorPessoaCadastroServico, "confirmadorPessoaCadastroServico é obrigatório");
        } else {
            this.provisionadorPerfilSistemaServico = provisionadorPerfilSistemaServico;
            this.confirmadorPessoaCadastroServico = confirmadorPessoaCadastroServico;
        }
        this.disponibilidadeUsuarioSistemaService = disponibilidadeUsuarioSistemaService;
        this.canalEnvioCodigoCadastroEmail = Objects.requireNonNull(
                canalEnvioCodigoCadastroEmail, "canalEnvioCodigoCadastroEmail é obrigatório");
        this.canalNotificacaoTentativaCadastroEmail = Objects.requireNonNull(
                canalNotificacaoTentativaCadastroEmail, "canalNotificacaoTentativaCadastroEmail é obrigatório");
        this.dispositivoProperties = Objects.requireNonNull(dispositivoProperties, "dispositivoProperties é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.sincronizacaoModeloMultiappService = sincronizacaoModeloMultiappService;
        this.registradorPendenciaIntegracaoProdutoService = registradorPendenciaIntegracaoProdutoService;
        this.contextoSocialPendenteJdbc = contextoSocialPendenteJdbc;
        this.resolvedorProjetoFluxoPublico = resolvedorProjetoFluxoPublico;
        this.provisionamentoIdentidadeServiceCompat = provisionamentoIdentidadeServiceCompat;
    }

    public CadastroInternoRealizado cadastrar(final String nomeCompleto,
                                              final String emailPrincipal,
                                              final String telefonePrincipal,
                                              final CanalValidacaoTelefoneCadastro tipoValidacaoTelefone,
                                              final String senhaPura,
                                              final String sistemaSolicitante,
                                              final String ipSolicitante,
                                              final String userAgentSolicitante) {
        return cadastrarCompleto(
                TipoPessoaCadastro.FISICA,
                nomeCompleto,
                null,
                "",
                null,
                null,
                null,
                emailPrincipal,
                telefonePrincipal,
                tipoValidacaoTelefone,
                senhaPura,
                sistemaSolicitante,
                ipSolicitante,
                userAgentSolicitante
        );
    }

    public CadastroInternoRealizado cadastrarPublico(final TipoPessoaCadastro tipoPessoa,
                                                     final String nomeCompleto,
                                                     final String nomeFantasia,
                                                     final String usuario,
                                                     final SexoPessoaCadastro sexo,
                                                     final String paisNascimento,
                                                     final LocalDate dataNascimento,
                                                     final String emailPrincipal,
                                                     final String telefonePrincipal,
                                                     final CanalValidacaoTelefoneCadastro tipoValidacaoTelefone,
                                                     final String senhaPura,
                                                     final String sistemaSolicitante,
                                                     final String ipSolicitante,
                                                     final String userAgentSolicitante) {
        return cadastrarCompleto(
                Objects.requireNonNull(tipoPessoa, "tipoPessoa é obrigatório"),
                nomeCompleto,
                nomeFantasia,
                usuario,
                sexo,
                paisNascimento,
                dataNascimento,
                emailPrincipal,
                telefonePrincipal,
                tipoValidacaoTelefone,
                senhaPura,
                sistemaSolicitante,
                ipSolicitante,
                userAgentSolicitante
        );
    }

    public ConfirmacaoEmailCadastroInternoRealizada confirmarEmail(final UUID cadastroId, final String codigo) {
        ConfirmacaoEmailCadastroPublicoRealizada confirmacao = confirmarEmailDetalhado(cadastroId, codigo);
        return new ConfirmacaoEmailCadastroInternoRealizada(
                confirmacao.cadastroId(),
                confirmacao.subjectRemoto(),
                confirmacao.emailPrincipal(),
                confirmacao.emailConfirmado(),
                confirmacao.podeAutenticar()
        );
    }

    public ConfirmacaoEmailCadastroPublicoRealizada confirmarEmailPublico(final UUID cadastroId, final String codigo) {
        return confirmarEmailDetalhado(cadastroId, codigo);
    }

    public boolean usuarioDisponivelPublico(final String usuario) {
        return identificadorPublicoSistemaDisponivelPublico(usuario, SISTEMA_PUBLICO_PADRAO);
    }

    public boolean usuarioDisponivelPublico(final String usuario, final String sistemaSolicitante) {
        return identificadorPublicoSistemaDisponivelPublico(usuario, sistemaSolicitante);
    }

    public boolean identificadorPublicoSistemaDisponivelPublico(final String identificadorPublicoSistema) {
        return identificadorPublicoSistemaDisponivelPublico(
                identificadorPublicoSistema,
                SISTEMA_PUBLICO_PADRAO
        );
    }

    public boolean identificadorPublicoSistemaDisponivelPublico(final String identificadorPublicoSistema,
                                                                final String sistemaSolicitante) {
        return identificadorPublicoSistemaDisponivel(identificadorPublicoSistema, sistemaSolicitante);
    }

    public boolean identificadorPublicoSistemaDisponivel(final String identificadorPublicoSistema,
                                                         final String sistemaSolicitante) {
        String usuarioNormalizado = normalizarUsuarioOpcional(identificadorPublicoSistema);
        String sistemaNormalizado = normalizarOpcional(sistemaSolicitante);
        if (usuarioNormalizado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usuario é obrigatório.");
        }
        if (sistemaNormalizado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sistemaSolicitante é obrigatório.");
        }
        return usuarioDisponivelNormalizado(usuarioNormalizado, sistemaNormalizado);
    }

    public boolean usuarioDisponivel(final String usuario, final String sistemaSolicitante) {
        return identificadorPublicoSistemaDisponivel(usuario, sistemaSolicitante);
    }

    public boolean possuiCadastroPendenteEmailPublico(final String emailPrincipal) {
        String emailNormalizado = obrigatorio(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        return cadastroContaRepositorio.findByEmailPrincipal(emailNormalizado)
                .map(this::cadastroPendenteEmail)
                .orElse(false);
    }

    public Optional<UUID> buscarCadastroPendenteEmailPublico(final String emailPrincipal) {
        String emailNormalizado = obrigatorio(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        return cadastroContaRepositorio.findByEmailPrincipal(emailNormalizado)
                .filter(this::cadastroPendenteEmail)
                .map(CadastroConta::getCadastroId);
    }

    public Optional<ContextoPessoaPerfilSistema> buscarContextoCentralPorEmailPublico(final String emailPrincipal) {
        String emailNormalizado = obrigatorio(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        return cadastroContaRepositorio.findByEmailPrincipal(emailNormalizado)
                .filter(CadastroConta::emailJaConfirmado)
                .filter(cadastro -> cadastro.getPessoaIdPerfil() != null)
                .map(this::mapearContextoCentralFallback);
    }

    public Optional<ContextoPessoaPerfilSistema> buscarContextoCentralPorSubPublico(final String subPessoa) {
        String subNormalizado = obrigatorio(subPessoa, "subPessoa");
        return buscarContextoProdutoPorSubTolerante(subNormalizado)
                .or(() -> cadastroContaRepositorio.findBySubjectRemoto(subNormalizado)
                        .filter(CadastroConta::emailJaConfirmado)
                        .filter(cadastro -> cadastro.getPessoaIdPerfil() != null)
                        .map(this::mapearContextoCentralFallback));
    }

    public void reenviarCodigoEmail(final UUID cadastroId) {
        if (cadastroId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CadastroId é obrigatório.");
        }
        CadastroConta cadastroConta = cadastroContaRepositorio.findByCadastroId(cadastroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        if (cadastroConta.emailJaConfirmado()) {
            return;
        }
        if (cadastroConta.ultrapassouReenviosEmail(dispositivoProperties.getCodigo().getReenviosMaximos())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "O limite de reenvios foi atingido.");
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        String codigoClaro = gerarCodigoNumerico();
        cadastroConta.atualizarCodigoEmail(
                hashCodigoEmail(codigoClaro, cadastroConta.getEmailPrincipal(), cadastroConta.getSubjectRemoto()),
                agora,
                agora.plusHours(dispositivoProperties.getCodigo().getExpiracaoHoras()),
                agora
        );
        sincronizarCadastroSeConfigurado(cadastroConta);
        canalEnvioCodigoCadastroEmail.enviar(cadastroConta, codigoClaro);
    }

    public void cancelarCadastroPendentePublico(final UUID cadastroId) {
        if (cadastroId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CadastroId é obrigatório.");
        }
        cadastroContaRepositorio.findByCadastroId(cadastroId)
                .filter(this::cadastroPendenteEmail)
                .ifPresent(this::removerCadastroPendente);
    }

    public int expurgarCadastrosPendentesExpirados() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusHours(48);
        List<CadastroConta> expirados = cadastroContaRepositorio.findByStatusAndCriadoEmBefore(
                StatusCadastroConta.PENDENTE_EMAIL,
                limite
        );
        int removidos = 0;
        for (CadastroConta cadastroConta : expirados) {
            try {
                removerCadastroPendente(cadastroConta);
                removidos += 1;
            } catch (RuntimeException ex) {
                LOGGER.warn(
                        "Falha ao expurgar cadastro pendente expirado cadastroId={} subjectRemoto={}",
                        cadastroConta.getCadastroId(),
                        cadastroConta.getSubjectRemoto(),
                        ex
                );
            }
        }
        return removidos;
    }

    private CadastroInternoRealizado cadastrarCompleto(final TipoPessoaCadastro tipoPessoa,
                                                       final String nomeCompleto,
                                                       final String nomeFantasia,
                                                       final String usuario,
                                                       final SexoPessoaCadastro sexo,
                                                       final String paisNascimento,
                                                       final LocalDate dataNascimento,
                                                       final String emailPrincipal,
                                                       final String telefonePrincipal,
                                                       final CanalValidacaoTelefoneCadastro tipoValidacaoTelefone,
                                                       final String senhaPura,
                                                       final String sistemaSolicitante,
                                                       final String ipSolicitante,
                                                       final String userAgentSolicitante) {
        String nomeNormalizado = obrigatorio(nomeCompleto, "nomeCompleto");
        String emailNormalizado = obrigatorio(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        String telefoneNormalizado = normalizarOpcional(telefonePrincipal);
        CanalValidacaoTelefoneCadastro tipoValidacaoTelefoneNormalizado =
                telefoneNormalizado == null
                        ? null
                        : Objects.requireNonNullElse(tipoValidacaoTelefone, CanalValidacaoTelefoneCadastro.SMS);
        String senhaNormalizada = obrigatorio(senhaPura, "senhaPura");
        RecuperacaoSenhaService.validarPoliticaSenha(senhaNormalizada);
        String sistemaNormalizado = obrigatorio(sistemaSolicitante, "sistemaSolicitante");
        String usuarioNormalizado = normalizarUsuarioOpcional(usuario);
        String nomeFantasiaNormalizado = normalizarOpcional(nomeFantasia);
        String paisNascimentoNormalizado = normalizarOpcional(paisNascimento);

        validarDuplicidadeUsuario(usuarioNormalizado, sistemaNormalizado);
        validarDuplicidadeEmail(emailNormalizado);

        CadastroKeycloakProvisionado cadastroKeycloak = clienteAdministracaoCadastroKeycloak.criarUsuarioPendente(
                nomeNormalizado,
                emailNormalizado,
                senhaNormalizada
        );

        OffsetDateTime agora = OffsetDateTime.now(clock);
        String codigoClaro = gerarCodigoNumerico();
        CadastroConta cadastroConta = cadastroContaRepositorio.save(new CadastroConta(
                UUID.randomUUID(),
                cadastroKeycloak.subjectRemoto(),
                Objects.requireNonNull(tipoPessoa, "tipoPessoa é obrigatório"),
                nomeNormalizado,
                nomeFantasiaNormalizado,
                Objects.requireNonNullElse(usuarioNormalizado, ""),
                sexo,
                paisNascimentoNormalizado,
                dataNascimento,
                emailNormalizado,
                telefoneNormalizado,
                tipoValidacaoTelefoneNormalizado,
                hashCodigoEmail(codigoClaro, emailNormalizado, cadastroKeycloak.subjectRemoto()),
                agora,
                agora.plusHours(dispositivoProperties.getCodigo().getExpiracaoHoras()),
                sistemaNormalizado,
                ipSolicitante,
                userAgentSolicitante,
                agora,
                agora
        ));

        if (provisionamentoIdentidadeServiceCompat != null) {
            provisionamentoIdentidadeServiceCompat.provisionarCadastroPendente(
                    cadastroKeycloak.subjectRemoto(),
                    emailNormalizado,
                    nomeNormalizado,
                    agora
            );
        }

        sincronizarCadastroSeConfigurado(cadastroConta);
        canalEnvioCodigoCadastroEmail.enviar(cadastroConta, codigoClaro);

        return new CadastroInternoRealizado(
                cadastroConta.getCadastroId(),
                cadastroKeycloak.subjectRemoto(),
                emailNormalizado,
                true
        );
    }

    private ConfirmacaoEmailCadastroPublicoRealizada confirmarEmailDetalhado(final UUID cadastroId, final String codigo) {
        if (cadastroId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CadastroId é obrigatório.");
        }
        String codigoNormalizado = obrigatorio(codigo, "codigo");
        CadastroConta cadastroConta = cadastroContaRepositorio.findByCadastroId(cadastroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        if (cadastroConta.emailJaConfirmado()) {
            return montarRespostaConfirmacao(cadastroConta, cadastroConta.getStatus().name());
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (cadastroConta.codigoEmailExpirado(agora)) {
            throw new ResponseStatusException(HttpStatus.GONE, "O código de confirmação do cadastro expirou.");
        }
        if (cadastroConta.getTentativasConfirmacaoEmail() >= dispositivoProperties.getCodigo().getTentativasMaximas()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "O limite de tentativas de confirmação foi atingido."
            );
        }

        cadastroConta.registrarTentativaConfirmacao(agora);
        if (!Objects.equals(
                cadastroConta.getCodigoEmailHash(),
                hashCodigoEmail(codigoNormalizado, cadastroConta.getEmailPrincipal(), cadastroConta.getSubjectRemoto()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O código de confirmação informado é inválido.");
        }

        String statusPerfilSistema = "EMAIL_CONFIRMADO";
        if (ehFluxoCadastroPublico(cadastroConta)) {
            PessoaCanonicaConfirmada pessoa = confirmarPessoaCanonicaPublica(cadastroConta, agora);
            ResultadoProvisionamentoPerfilPublico provisionamento =
                    provisionarPerfilSistemaPublico(cadastroConta, pessoa.pessoaId());
            if (provisionamento.perfilSistemaId() != null && !provisionamento.perfilSistemaId().isBlank()) {
                cadastroConta.definirProvisionamentoPerfil(
                        pessoa.pessoaId(),
                        provisionamento.perfilSistemaId(),
                        agora
                );
            } else {
                cadastroConta.definirPessoaIdPerfil(pessoa.pessoaId(), agora);
            }
            statusPerfilSistema = provisionamento.statusPerfilSistema();
        } else {
            buscarContextoProdutoPorSubTolerante(cadastroConta.getSubjectRemoto())
                    .ifPresent(contexto -> {
                        cadastroConta.definirPessoaIdPerfil(contexto.pessoaId(), agora);
                        if (contexto.perfilSistemaId() != null && !contexto.perfilSistemaId().isBlank()) {
                            cadastroConta.definirProvisionamentoPerfil(
                                    contexto.pessoaId(),
                                    contexto.perfilSistemaId(),
                                    agora
                            );
                        }
                    });
            if (provisionamentoIdentidadeServiceCompat != null) {
                provisionamentoIdentidadeServiceCompat.confirmarEmailCadastro(
                        cadastroConta.getSubjectRemoto(),
                        cadastroConta.getEmailPrincipal(),
                        cadastroConta.getNomeCompleto(),
                        agora
                );
            }
            statusPerfilSistema = buscarContextoProdutoPorSubTolerante(cadastroConta.getSubjectRemoto())
                    .map(ContextoPessoaPerfilSistema::statusPerfilSistema)
                    .filter(valor -> valor != null && !valor.isBlank())
                    .orElse(statusPerfilSistema);
        }

        clienteAdministracaoCadastroKeycloak.confirmarEmailEAtivarUsuario(
                cadastroConta.getSubjectRemoto(),
                cadastroConta.getNomeCompleto(),
                cadastroConta.getDataNascimento()
        );
        vincularIdentidadesSociaisPendentesDoCadastro(cadastroConta);
        cadastroConta.marcarEmailConfirmado(agora);
        sincronizarCadastroSeConfigurado(cadastroConta);

        return montarRespostaConfirmacao(cadastroConta, statusPerfilSistema);
    }

    private void vincularIdentidadesSociaisPendentesDoCadastro(final CadastroConta cadastroConta) {
        if (contextoSocialPendenteJdbc == null || resolvedorProjetoFluxoPublico == null || cadastroConta == null) {
            return;
        }
        try {
            ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico
                    .resolverAtivo(cadastroConta.getSistemaSolicitante());
            if (projeto == null) {
                return;
            }
            List<ContextoSocialPendenteJdbc.ContextoSocialPendenteCadastro> contextos = contextoSocialPendenteJdbc
                    .listarPendentesAberturaCadastro(projeto.clienteEcossistemaId(), cadastroConta.getEmailPrincipal());
            for (ContextoSocialPendenteJdbc.ContextoSocialPendenteCadastro contexto : contextos) {
                Optional<ProvedorVinculoSocial> provedor = ProvedorVinculoSocial.fromAlias(contexto.provedor());
                if (provedor.isEmpty()) {
                    LOGGER.warn(
                            "contexto_social_pendente_ignorado_provedor_desconhecido cadastroId={} provedor={}",
                            cadastroConta.getCadastroId(),
                            contexto.provedor()
                    );
                    continue;
                }
                try {
                    clienteAdministracaoCadastroKeycloak.vincularIdentidadeFederada(
                            cadastroConta.getSubjectRemoto(),
                            new IdentidadeFederadaKeycloak(
                                    provedor.orElseThrow(),
                                    contexto.identificadorExterno(),
                                    contexto.nomeUsuarioExterno(),
                                    contexto.nomeExibicaoExterno(),
                                    contexto.urlAvatarExterno()
                            )
                    );
                    contextoSocialPendenteJdbc.consumir(contexto.id());
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "falha_vinculo_social_pendente_pos_confirmacao cadastroId={} contextoId={} provedor={} motivo={}",
                            cadastroConta.getCadastroId(),
                            contexto.id(),
                            contexto.provedor(),
                            exception.getMessage()
                    );
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "falha_resolucao_contextos_sociais_pendentes_pos_confirmacao cadastroId={} sistema={} motivo={}",
                    cadastroConta.getCadastroId(),
                    cadastroConta.getSistemaSolicitante(),
                    exception.getMessage()
            );
        }
    }

    private PessoaCanonicaConfirmada confirmarPessoaCanonicaPublica(final CadastroConta cadastroConta,
                                                                    final OffsetDateTime agora) {
        ConfirmadorPessoaCadastroServico confirmador = Objects.requireNonNull(
                confirmadorPessoaCadastroServico,
                "confirmadorPessoaCadastroServico é obrigatório para o fluxo público"
        );
        PessoaCanonicaConfirmada pessoa = confirmador.confirmarEmailCadastro(
                cadastroConta.getSubjectRemoto(),
                cadastroConta.getEmailPrincipal(),
                cadastroConta.getNomeCompleto(),
                agora
        );
        cadastroConta.definirPessoaIdPerfil(pessoa.pessoaId(), agora);
        return pessoa;
    }

    private ResultadoProvisionamentoPerfilPublico provisionarPerfilSistemaPublico(final CadastroConta cadastroConta,
                                                                                  final Long pessoaIdCentral) {
        ProvisionadorPerfilSistemaServico provisionador = Objects.requireNonNull(
                provisionadorPerfilSistemaServico,
                "provisionadorPerfilSistemaServico é obrigatório para o fluxo público"
        );
        try {
            ProvisionamentoPerfilSistemaRealizado provisionamento =
                    provisionador.provisionarCadastroConfirmado(cadastroConta, pessoaIdCentral);
            return new ResultadoProvisionamentoPerfilPublico(
                    provisionamento.perfilSistemaId(),
                    provisionamento.statusPerfilSistema()
            );
        } catch (ResponseStatusException ex) {
            if (!falhaProvisionamentoProdutoToleravel(ex)) {
                throw ex;
            }
            registrarPendenciaProvisionamentoPerfilSistema(cadastroConta, pessoaIdCentral, ex);
            LOGGER.warn(
                    "provisionamento_perfil_sistema_pendente cadastroId={} sistema={} motivo={}",
                    cadastroConta.getCadastroId(),
                    cadastroConta.getSistemaSolicitante(),
                    ex.getReason()
            );
            return new ResultadoProvisionamentoPerfilPublico(
                    null,
                    STATUS_PENDENTE_LIBERACAO_PRODUTO
            );
        }
    }

    private ConfirmacaoEmailCadastroPublicoRealizada montarRespostaConfirmacao(final CadastroConta cadastroConta,
                                                                               final String statusPerfilSistemaPadrao) {
        ContextoPessoaPerfilSistema contexto = buscarContextoProdutoPorSubTolerante(cadastroConta.getSubjectRemoto())
                .orElse(null);
        String perfilSistemaId = cadastroConta.getPerfilSistemaId();
        if ((perfilSistemaId == null || perfilSistemaId.isBlank()) && contexto != null) {
            perfilSistemaId = contexto.perfilSistemaId();
        }
        String statusPerfilSistema = contexto == null
                || contexto.statusPerfilSistema() == null
                || contexto.statusPerfilSistema().isBlank()
                ? statusPerfilSistemaPadrao
                : contexto.statusPerfilSistema();
        return new ConfirmacaoEmailCadastroPublicoRealizada(
                cadastroConta.getCadastroId(),
                cadastroConta.getSubjectRemoto(),
                cadastroConta.getEmailPrincipal(),
                Objects.requireNonNullElse(perfilSistemaId, ""),
                statusPerfilSistema,
                true,
                true,
                PROXIMO_PASSO_LOGIN
        );
    }

    private boolean falhaProvisionamentoProdutoToleravel(final ResponseStatusException ex) {
        return ex.getStatusCode().is5xxServerError();
    }

    private void registrarPendenciaProvisionamentoPerfilSistema(final CadastroConta cadastroConta,
                                                                final Long pessoaIdCentral,
                                                                final ResponseStatusException ex) {
        RegistradorPendenciaIntegracaoProdutoService registrador = Objects.requireNonNull(
                registradorPendenciaIntegracaoProdutoService,
                "registradorPendenciaIntegracaoProdutoService é obrigatório para tolerar falha de provisionamento"
        );
        registrador.registrarProvisionamentoPerfilSistema(
                cadastroConta,
                pessoaIdCentral,
                "PROVISIONAMENTO_PERFIL_SISTEMA_HTTP_" + ex.getStatusCode().value(),
                Objects.requireNonNullElse(
                        ex.getReason(),
                        "Falha toleravel ao provisionar o perfil do sistema."
                )
        );
    }

    private ContextoPessoaPerfilSistema mapearContextoCentralFallback(final CadastroConta cadastroConta) {
        String statusPerfilSistema = cadastroConta.getPerfilSistemaId() == null || cadastroConta.getPerfilSistemaId().isBlank()
                ? STATUS_PENDENTE_LIBERACAO_PRODUTO
                : "LIBERADO";
        return new ContextoPessoaPerfilSistema(
                cadastroConta.getPessoaIdPerfil(),
                cadastroConta.getSubjectRemoto(),
                cadastroConta.getEmailPrincipal(),
                cadastroConta.getNomeCompleto(),
                cadastroConta.getPerfilSistemaId(),
                statusPerfilSistema
        );
    }

    private void validarDuplicidadeUsuario(final String usuarioNormalizado,
                                           final String sistemaSolicitanteNormalizado) {
        if (usuarioNormalizado == null || usuarioNormalizado.isBlank()) {
            return;
        }
        if (!usuarioDisponivelNormalizado(usuarioNormalizado, sistemaSolicitanteNormalizado)) {
            throw FluxoPublicoException.conflito("usuario_indisponivel", "Este usuário não está disponível.");
        }
    }

    private boolean usuarioDisponivelNormalizado(final String usuarioNormalizado,
                                                 final String sistemaSolicitanteNormalizado) {
        if (disponibilidadeUsuarioSistemaService == null) {
            return !cadastroContaRepositorio.findByUsuarioIgnoreCase(usuarioNormalizado).isPresent();
        }
        return disponibilidadeUsuarioSistemaService.identificadorPublicoSistemaDisponivel(
                usuarioNormalizado,
                sistemaSolicitanteNormalizado
        );
    }

    private void validarDuplicidadeEmail(final String emailNormalizado) {
        Optional<CadastroConta> cadastroExistente = cadastroContaRepositorio.findByEmailPrincipal(emailNormalizado);
        if (cadastroExistente.isPresent()) {
            throw FluxoPublicoException.conflito(
                    "cadastro_nao_disponivel",
                    "Não foi possível concluir o cadastro com os dados informados."
            );
        }
        buscarContextoProdutoPorEmailTolerante(emailNormalizado)
                .ifPresent(contexto -> {
                    notificarTentativaCadastroContaExistente(emailNormalizado);
                    throw FluxoPublicoException.conflito(
                            "cadastro_nao_disponivel",
                            "Não foi possível concluir o cadastro com os dados informados."
                    );
                });
    }

    private void notificarTentativaCadastroContaExistente(final String emailNormalizado) {
        try {
            canalNotificacaoTentativaCadastroEmail.notificar(emailNormalizado);
        } catch (RuntimeException ex) {
            LOGGER.warn("Falha ao enviar o aviso de tentativa de cadastro para {}", emailNormalizado, ex);
        }
    }

    private boolean cadastroPendenteEmail(final CadastroConta cadastroConta) {
        return cadastroConta.getStatus() == StatusCadastroConta.PENDENTE_EMAIL;
    }

    private void removerCadastroPendente(final CadastroConta cadastroConta) {
        clienteAdministracaoCadastroKeycloak.removerUsuarioPendente(cadastroConta.getSubjectRemoto());
        cadastroContaRepositorio.delete(cadastroConta);
        removerCadastroSeConfigurado(cadastroConta.getCadastroId());
    }

    private boolean ehFluxoCadastroPublico(final CadastroConta cadastroConta) {
        return cadastroConta.getUsuario() != null && !cadastroConta.getUsuario().isBlank();
    }

    private String gerarCodigoNumerico() {
        int limite = (int) Math.pow(10, dispositivoProperties.getCodigo().getTamanho());
        String formato = "%0" + dispositivoProperties.getCodigo().getTamanho() + "d";
        return formato.formatted(SECURE_RANDOM.nextInt(limite));
    }

    private String hashCodigoEmail(final String codigoClaro,
                                   final String emailPrincipal,
                                   final String subjectRemoto) {
        String material = obrigatorio(codigoClaro, "codigoClaro")
                + "|" + obrigatorio(emailPrincipal, "emailPrincipal")
                + "|" + obrigatorio(subjectRemoto, "subjectRemoto")
                + "|CADASTRO_EMAIL";
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(
                    dispositivoProperties.getCodigo().getSegredoHmac().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALG
            ));
            return hexFormat.formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao calcular o hash do código de confirmação do cadastro.", ex);
        }
    }

    private static String obrigatorio(final String valor, final String campo) {
        String normalizado = Objects.requireNonNull(valor, campo + " é obrigatório").trim();
        if (normalizado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " é obrigatório.");
        }
        return normalizado;
    }

    private static String normalizarOpcional(final String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isBlank() ? null : normalizado;
    }

    private void sincronizarCadastroSeConfigurado(final CadastroConta cadastroConta) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarCadastro(cadastroConta);
        }
    }

    private void removerCadastroSeConfigurado(final UUID cadastroId) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.removerCadastro(cadastroId);
        }
    }

    private Optional<ContextoPessoaPerfilSistema> buscarContextoProdutoPorEmailTolerante(final String email) {
        try {
            return clienteContextoPessoaPerfilSistema.buscarPorEmail(email);
        } catch (RuntimeException ex) {
            LOGGER.warn("contexto_produto_indisponivel_busca_email email={} motivo={}", email, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ContextoPessoaPerfilSistema> buscarContextoProdutoPorSubTolerante(final String sub) {
        try {
            return clienteContextoPessoaPerfilSistema.buscarPorSub(sub);
        } catch (RuntimeException ex) {
            LOGGER.warn("contexto_produto_indisponivel_busca_sub sub={} motivo={}", sub, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String normalizarUsuarioOpcional(final String valor) {
        String normalizado = normalizarOpcional(valor);
        return normalizado == null ? null : normalizado.toLowerCase(Locale.ROOT);
    }

    private record ResultadoProvisionamentoPerfilPublico(
            String perfilSistemaId,
            String statusPerfilSistema
    ) {
    }

    private static final class ClienteContextoPessoaPerfilSistemaLegado implements ClienteContextoPessoaPerfilSistema {

        private final FormaAcessoRepositorio formaAcessoRepositorio;
        private final ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

        private ClienteContextoPessoaPerfilSistemaLegado(final FormaAcessoRepositorio formaAcessoRepositorio,
                                                  final ProvisionamentoIdentidadeService provisionamentoIdentidadeService) {
            this.formaAcessoRepositorio = formaAcessoRepositorio;
            this.provisionamentoIdentidadeService = provisionamentoIdentidadeService;
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorPessoaId(final Long pessoaId) {
            return Optional.empty();
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorSub(final String sub) {
            return provisionamentoIdentidadeService.localizarPessoaPorSub(sub)
                    .map(pessoa -> new ContextoPessoaPerfilSistema(
                            pessoa.getId(),
                            pessoa.getSub(),
                            pessoa.getEmail(),
                            pessoa.getNome(),
                            null,
                            null));
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorEmail(final String email) {
            return formaAcessoRepositorio.findByTipoAndProvedorAndIdentificador(
                            TipoFormaAcesso.EMAIL_SENHA, "EMAIL", email)
                    .map(formaAcesso -> formaAcesso.getPessoa())
                    .map(pessoa -> new ContextoPessoaPerfilSistema(
                            pessoa.getId(),
                            pessoa.getSub(),
                            pessoa.getEmail(),
                            pessoa.getNome(),
                            null,
                            null));
        }
    }
}
