package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusCodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.CodigoVerificacaoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.RegistroDispositivoRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.apresentacao.dto.ReenvioCodigoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orquestra o ciclo de vida do registro e confirmacao de dispositivos moveis.
 */
@Service
public class RegistroDispositivoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroDispositivoService.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegistroDispositivoRepositorio registroRepositorio;
    private final CodigoVerificacaoRepositorio codigoRepositorio;
    private final TokenDispositivoService tokenDispositivoService;
    private final ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService;
    private final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;
    private final DispositivoIdentidadeService dispositivoIdentidadeService;
    private final DispositivoProperties propriedades;
    private final AuditoriaService auditoriaService;
    private final Map<CanalVerificacao, CanalEnvioCodigo> canaisEnvio;
    private final Clock clock;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;
    private final HexFormat hexFormat = HexFormat.of();

    @Autowired
    public RegistroDispositivoService(RegistroDispositivoRepositorio registroRepositorio,
                                      CodigoVerificacaoRepositorio codigoRepositorio,
                                      TokenDispositivoService tokenDispositivoService,
                                      ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService,
                                      DispositivoIdentidadeService dispositivoIdentidadeService,
                                      DispositivoProperties propriedades,
                                      AuditoriaService auditoriaService,
                                      List<CanalEnvioCodigo> canaisEnvio,
                                      Clock clock,
                                      SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService) {
        this(
                registroRepositorio,
                codigoRepositorio,
                tokenDispositivoService,
                resolvedorContextoAutenticacaoService,
                null,
                dispositivoIdentidadeService,
                propriedades,
                auditoriaService,
                canaisEnvio,
                clock,
                sincronizacaoModeloMultiappService
        );
    }

    private RegistroDispositivoService(final RegistroDispositivoRepositorio registroRepositorio,
                                       final CodigoVerificacaoRepositorio codigoRepositorio,
                                       final TokenDispositivoService tokenDispositivoService,
                                       final ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService,
                                       final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                       final DispositivoIdentidadeService dispositivoIdentidadeService,
                                       final DispositivoProperties propriedades,
                                       final AuditoriaService auditoriaService,
                                       final List<CanalEnvioCodigo> canaisEnvio,
                                       final Clock clock,
                                       final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService) {
        this.registroRepositorio = registroRepositorio;
        this.codigoRepositorio = codigoRepositorio;
        this.tokenDispositivoService = tokenDispositivoService;
        this.resolvedorContextoAutenticacaoService = resolvedorContextoAutenticacaoService;
        this.clienteContextoPessoaPerfilSistema = clienteContextoPessoaPerfilSistema;
        this.dispositivoIdentidadeService = dispositivoIdentidadeService;
        this.propriedades = propriedades;
        this.auditoriaService = auditoriaService;
        this.canaisEnvio = construirMapaCanais(canaisEnvio);
        this.clock = clock;
        this.sincronizacaoModeloMultiappService = sincronizacaoModeloMultiappService;
    }

    public RegistroDispositivoService(final RegistroDispositivoRepositorio registroRepositorio,
                                      final CodigoVerificacaoRepositorio codigoRepositorio,
                                      final TokenDispositivoService tokenDispositivoService,
                                      final ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                      final DispositivoIdentidadeService dispositivoIdentidadeService,
                                      final DispositivoProperties propriedades,
                                      final AuditoriaService auditoriaService,
                                      final List<CanalEnvioCodigo> canaisEnvio,
                                      final Clock clock) {
        this(
                registroRepositorio,
                codigoRepositorio,
                tokenDispositivoService,
                null,
                new ClienteContextoPessoaPerfilSistemaLegado(Objects.requireNonNull(provisionamentoIdentidadeService,
                        "provisionamentoIdentidadeService é obrigatório")),
                dispositivoIdentidadeService,
                propriedades,
                auditoriaService,
                canaisEnvio,
                clock,
                null
        );
    }

    @Transactional
    public RegistroDispositivoResponse solicitarRegistroParaSessao(final ContextoPessoaPerfilSistema contexto,
                                                                   final DispositivoSessaoApiRequest dispositivo) {
        ContextoPessoaPerfilSistema contextoObrigatorio = Objects.requireNonNull(contexto, "contexto é obrigatório");
        DispositivoSessaoApiRequest dispositivoObrigatorio = Objects.requireNonNull(
                dispositivo, "dispositivo é obrigatório");
        RegistroDispositivoRequest request = new RegistroDispositivoRequest();
        request.setEmail(normalizarObrigatorio(contextoObrigatorio.emailPrincipal(), "emailPrincipal"));
        request.setFingerprint(FingerprintDispositivoUtil.derivar(dispositivoObrigatorio, propriedades));
        request.setPlataforma(normalizarObrigatorio(dispositivoObrigatorio.plataforma(), "plataforma"));
        request.setVersaoAplicativo(normalizarObrigatorio(dispositivoObrigatorio.versaoApp(), "versaoApp"));
        return solicitarRegistro(request, Optional.of(jwtDoContexto(contextoObrigatorio)));
    }

    @Transactional
    public RegistroDispositivoResponse solicitarRegistro(RegistroDispositivoRequest request, Optional<Jwt> jwtOpt) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        OffsetDateTime expiraEm = agora.plusHours(propriedades.getCodigo().getExpiracaoHoras());
        UUID id = UUID.randomUUID();
        String emailNormalizado = normalizarObrigatorio(request.getEmail(), "email").toLowerCase(Locale.ROOT);
        String usuarioSub = jwtOpt.map(Jwt::getSubject).orElse(null);
        Long pessoaIdPerfil = resolverContextoPessoa(usuarioSub, emailNormalizado)
                .map(ContextoPessoaPerfilSistema::pessoaId)
                .orElse(null);
        String telefoneNormalizado = normalizarTelefone(request.getTelefone());
        RegistroDispositivo registro = new RegistroDispositivo(
                id,
                usuarioSub,
                pessoaIdPerfil,
                emailNormalizado,
                telefoneNormalizado,
                normalizarObrigatorio(request.getFingerprint(), "fingerprint"),
                normalizarObrigatorio(request.getPlataforma(), "plataforma"),
                normalizarObrigatorio(request.getVersaoAplicativo(), "versaoAplicativo"),
                request.getChavePublica(),
                StatusRegistroDispositivo.PENDENTE,
                agora,
                expiraEm
        );

        List<CodigoGerado> codigosGerados = new ArrayList<>();
        codigosGerados.add(gerarCodigo(registro, CanalVerificacao.EMAIL, emailNormalizado, agora, expiraEm));
        if (StringUtils.hasText(telefoneNormalizado)) {
            codigosGerados.add(gerarCodigo(
                    registro,
                    CanalVerificacao.SMS,
                    telefoneNormalizado,
                    agora,
                    expiraEm));
        }
        codigosGerados.forEach(codigo -> registro.adicionarCodigo(codigo.entidade()));

        registroRepositorio.save(registro);
        sincronizarRegistroSeConfigurado(registro);

        codigosGerados.forEach(this::enviarCodigo);

        auditoriaService.registrarEvento("DISPOSITIVO_REGISTRO_SOLICITADO",
                jwtOpt.map(Jwt::getSubject).orElse(emailNormalizado),
                "Registro de dispositivo iniciado");

        LOGGER.info("Registro de dispositivo criado id={} email={} fingerprint={} canais={}",
                id,
                emailNormalizado,
                registro.getFingerprint(),
                codigosGerados.stream().map(codigo -> codigo.entidade().getCanal()).toList());

        return new RegistroDispositivoResponse(
                id,
                expiraEm,
                StatusRegistroDispositivo.PENDENTE,
                codigosGerados.stream().map(codigo -> codigo.entidade().getCanal()).toList());
    }

    @Transactional
    public ConfirmacaoRegistroResponse confirmarRegistro(UUID id,
                                                         ConfirmacaoRegistroRequest request,
                                                         Optional<Jwt> jwtOpt) {
        if (id == null) {
            throw new IllegalArgumentException("Id do registro obrigatorio.");
        }
        RegistroDispositivo registro = registroRepositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
        OffsetDateTime agora = OffsetDateTime.now(clock);

        if (registro.getStatus() != StatusRegistroDispositivo.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro não está pendente");
        }
        if (registro.getExpiraEm().isBefore(agora)) {
            registro.definirStatus(StatusRegistroDispositivo.EXPIRADO, agora);
            sincronizarRegistroSeConfigurado(registro);
            throw new ResponseStatusException(HttpStatus.GONE, "Registro expirado");
        }

        CodigoVerificacao codigoEmail = registro.codigoPorCanal(CanalVerificacao.EMAIL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Código e-mail ausente"));

        try {
            validarCodigo(codigoEmail, request.getCodigoEmail(), agora);
            registro.codigoPorCanal(CanalVerificacao.SMS)
                    .ifPresent(codigoSms -> validarCodigo(codigoSms, request.getCodigoSms(), agora));
        } catch (RuntimeException ex) {
            sincronizarRegistroSeConfigurado(registro);
            throw ex;
        }

        String usuarioSub = resolverUsuarioSub(registro, jwtOpt);
        Long pessoaIdPerfil = registro.getPessoaIdPerfil()
                .or(() -> resolverContextoPessoa(usuarioSub, registro.getEmail()).map(ContextoPessoaPerfilSistema::pessoaId))
                .orElse(null);
        registro.definirUsuarioSub(usuarioSub);
        registro.definirPessoaIdPerfil(pessoaIdPerfil);
        registro.definirStatus(StatusRegistroDispositivo.CONFIRMADO, agora);
        DispositivoIdentidade dispositivo = dispositivoIdentidadeService.garantirDispositivo(usuarioSub, pessoaIdPerfil, registro);

        ConfirmacaoRegistroResponse response = emitirToken(registro, dispositivo, usuarioSub);
        sincronizarRegistroSeConfigurado(registro);

        auditoriaService.registrarEvento("DISPOSITIVO_VERIFICACAO_SUCESSO",
                usuarioSub,
                "Registro de dispositivo confirmado");

        LOGGER.info("Registro de dispositivo confirmado id={} usuarioSub={}", id, usuarioSub);

        return response;
    }

    @Transactional
    public void reenviarCodigos(UUID id, ReenvioCodigoRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Id do registro obrigatorio.");
        }
        RegistroDispositivo registro = registroRepositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (registro.getStatus() != StatusRegistroDispositivo.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro não está pendente");
        }
        if (registro.getExpiraEm().isBefore(agora)) {
            registro.definirStatus(StatusRegistroDispositivo.EXPIRADO, agora);
            sincronizarRegistroSeConfigurado(registro);
            throw new ResponseStatusException(HttpStatus.GONE, "Registro expirado");
        }

        boolean reenviou = false;
        if (request.deveReenviarSms()) {
            reenviou |= registro.getTelefone()
                    .map(telefone -> reenviarCodigo(registro, CanalVerificacao.SMS, telefone, agora))
                    .orElse(false);
        }
        if (request.deveReenviarEmail()) {
            reenviou |= reenviarCodigo(registro, CanalVerificacao.EMAIL, registro.getEmail(), agora);
        }

        if (!reenviou) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de reenvios atingido");
        }

        registro.incrementarReenvios();
        sincronizarRegistroSeConfigurado(registro);

        auditoriaService.registrarEvento("DISPOSITIVO_CODIGO_REENVIADO",
                registro.getUsuarioSub().orElse(registro.getEmail()),
                "Reenvio de código solicitado");
    }

    @Transactional
    public void expirarRegistrosPendentes() {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        List<RegistroDispositivo> expirados = registroRepositorio.findByStatusInAndExpiraEmBefore(
                List.of(StatusRegistroDispositivo.PENDENTE),
                agora
        );
        for (RegistroDispositivo registro : expirados) {
            registro.definirStatus(StatusRegistroDispositivo.EXPIRADO, agora);
            registro.getCodigos().forEach(codigo -> codigo.marcarComoExpirado());
            sincronizarRegistroSeConfigurado(registro);
            auditoriaService.registrarEvento("DISPOSITIVO_REGISTRO_EXPIRADO",
                    registro.getUsuarioSub().orElse(registro.getEmail()),
                    "Registro expirado pelo scheduler");
            LOGGER.info("Registro de dispositivo expirado automaticamente id={}", registro.getId());
        }
    }

    @Transactional
    public void revogarToken(String usuarioSub, String token, MotivoRevogacaoToken motivo) {
        Objects.requireNonNull(usuarioSub, "usuarioSub é obrigatório");
        Objects.requireNonNull(token, "token é obrigatório");
        tokenDispositivoService.validarTokenAtivo(usuarioSub, token)
                .ifPresentOrElse(entidade -> {
                    entidade.revogar(motivo, OffsetDateTime.now(clock));
                    sincronizarTokenSeConfigurado(entidade);
                    auditoriaService.registrarEvento("DISPOSITIVO_TOKEN_REVOGADO",
                            usuarioSub,
                            "Token revogado pelo cliente");
                }, () -> LOGGER.warn("Tentativa de revogar token inexistente usuarioSub={}", usuarioSub));
    }

    private ConfirmacaoRegistroResponse emitirToken(RegistroDispositivo registro,
                                                    DispositivoIdentidade dispositivo,
                                                    String usuarioSub) {
        TokenDispositivoService.TokenEmitido tokenEmitido = tokenDispositivoService.emitirToken(registro, dispositivo, usuarioSub);
        return new ConfirmacaoRegistroResponse(
                tokenEmitido.tokenClaro(),
                tokenEmitido.entidade().getExpiraEm(),
                registro.getId(),
                tokenEmitido.entidade().getEmitidoEm()
        );
    }

    private void validarCodigo(CodigoVerificacao codigo, String codigoInformado, OffsetDateTime agora) {
        if (!StringUtils.hasText(codigoInformado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código em branco");
        }
        if (codigo.expirado(agora)) {
            codigo.marcarComoExpirado();
            throw new ResponseStatusException(HttpStatus.GONE, "Código expirado");
        }
        if (codigo.getStatus() == StatusCodigoVerificacao.BLOQUEADO) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Código bloqueado por tentativas excedidas");
        }
        if (!Objects.equals(codigo.getCodigoHash(), gerarHashCodigo(codigo.getRegistro().getId(), codigo.getCanal(), codigoInformado))) {
            codigo.registrarTentativaInvalida();
            if (codigo.getStatus() == StatusCodigoVerificacao.BLOQUEADO) {
                codigo.getRegistro().definirStatus(StatusRegistroDispositivo.BLOQUEADO, agora);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido");
        }
        codigo.marcarComoConfirmado(agora);
    }

    private boolean reenviarCodigo(RegistroDispositivo registro, CanalVerificacao canal, String destino, OffsetDateTime agora) {
        CodigoVerificacao codigo = registro.codigoPorCanal(canal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Código não encontrado"));
        if (codigo.ultrapassouReenvios()) {
            return false;
        }
        OffsetDateTime novaExpiracao = agora.plusHours(propriedades.getCodigo().getExpiracaoHoras());
        CodigoGerado novoCodigo = gerarCodigo(registro, canal, destino, agora, novaExpiracao);
        codigo.atualizarCodigo(novoCodigo.entidade().getCodigoHash(), agora, novaExpiracao);
        codigoRepositorio.save(codigo);
        enviarCodigo(new CodigoGerado(codigo, novoCodigo.codigoClaro()));
        return true;
    }

    private CodigoGerado gerarCodigo(RegistroDispositivo registro,
                                     CanalVerificacao canal,
                                     String destino,
                                     OffsetDateTime agora,
                                     OffsetDateTime expiraEm) {
        int tamanho = propriedades.getCodigo().getTamanho();
        String codigoClaro = gerarCodigoAleatorio(tamanho);
        String hash = gerarHashCodigo(registro.getId(), canal, codigoClaro);
        CodigoVerificacao entidade = new CodigoVerificacao(
                UUID.randomUUID(),
                canal,
                destino,
                hash,
                propriedades.getCodigo().getTentativasMaximas(),
                propriedades.getCodigo().getReenviosMaximos(),
                StatusCodigoVerificacao.PENDENTE,
                agora,
                expiraEm
        );
        return new CodigoGerado(entidade, codigoClaro);
    }

    private String gerarCodigoAleatorio(int tamanho) {
        StringBuilder builder = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            builder.append(SECURE_RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    private void enviarCodigo(CodigoGerado codigoGerado) {
        CanalEnvioCodigo canalEnvio = canaisEnvio.get(codigoGerado.entidade().getCanal());
        if (canalEnvio == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Canal de envio não configurado: " + codigoGerado.entidade().getCanal());
        }
        canalEnvio.enviar(codigoGerado.entidade().getRegistro(), codigoGerado.entidade().getDestino(), codigoGerado.codigoClaro());
    }

    private String gerarHashCodigo(UUID registroId, CanalVerificacao canal, String codigoClaro) {
        String segredo = propriedades.getCodigo().getSegredoHmac();
        if (!StringUtils.hasText(segredo)) {
            throw new IllegalStateException("identidade.dispositivo.codigo.segredo-hmac deve ser configurado");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            String chave = segredo + ":" + registroId + ":" + canal.name();
            mac.init(new SecretKeySpec(chave.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] resultado = mac.doFinal(codigoClaro.getBytes(StandardCharsets.UTF_8));
            return hexFormat.formatHex(resultado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gerar hash do código de verificação", e);
        }
    }

    private Map<CanalVerificacao, CanalEnvioCodigo> construirMapaCanais(List<CanalEnvioCodigo> canais) {
        Map<CanalVerificacao, CanalEnvioCodigo> mapa = new EnumMap<>(CanalVerificacao.class);
        for (CanalEnvioCodigo canalEnvio : canais) {
            mapa.put(canalEnvio.canal(), canalEnvio);
        }
        return mapa;
    }

    private String resolverUsuarioSub(final RegistroDispositivo registro, final Optional<Jwt> jwtOpt) {
        if (jwtOpt.isPresent()) {
            return jwtOpt.orElseThrow().getSubject();
        }
        return registro.getUsuarioSub()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuario não identificado para o dispositivo confirmado"));
    }

    private Optional<ContextoPessoaPerfilSistema> resolverContextoPessoa(final String usuarioSub, final String email) {
        if (resolvedorContextoAutenticacaoService != null) {
            return resolvedorContextoAutenticacaoService.buscarPorSubOuEmailPublico(usuarioSub, email);
        }
        Optional<ContextoPessoaPerfilSistema> porSub = StringUtils.hasText(usuarioSub)
                ? clienteContextoPessoaPerfilSistema.buscarPorSub(usuarioSub)
                : Optional.empty();
        if (porSub.isPresent()) {
            return porSub;
        }
        return StringUtils.hasText(email)
                ? clienteContextoPessoaPerfilSistema.buscarPorEmail(email)
                : Optional.empty();
    }

    private String normalizarObrigatorio(String valor, String campo) {
        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo obrigatório ausente: " + campo);
        }
        return valor.trim();
    }

    private String normalizarTelefone(String telefone) {
        if (!StringUtils.hasText(telefone)) {
            return null;
        }
        return telefone.trim();
    }

    private Jwt jwtDoContexto(final ContextoPessoaPerfilSistema contexto) {
        Jwt.Builder builder = Jwt.withTokenValue("registro-dispositivo-sessao")
                .header("alg", "none")
                .subject(normalizarObrigatorio(contexto.sub(), "sub"))
                .claim("email", normalizarObrigatorio(contexto.emailPrincipal(), "emailPrincipal"));
        if (StringUtils.hasText(contexto.usuario())) {
            String usuario = contexto.usuario().trim().toLowerCase(Locale.ROOT);
            builder
                    .claim("usuario", usuario)
                    .claim("preferred_username", usuario);
        }
        return builder.build();
    }

    private record CodigoGerado(CodigoVerificacao entidade, String codigoClaro) {
    }

    private void sincronizarRegistroSeConfigurado(final RegistroDispositivo registro) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarRegistroDispositivo(registro);
        }
    }

    private void sincronizarTokenSeConfigurado(final TokenDispositivo token) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarTokenDispositivo(token);
        }
    }

    private static final class ClienteContextoPessoaPerfilSistemaLegado implements ClienteContextoPessoaPerfilSistema {

        private final ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

        private ClienteContextoPessoaPerfilSistemaLegado(final ProvisionamentoIdentidadeService provisionamentoIdentidadeService) {
            this.provisionamentoIdentidadeService = provisionamentoIdentidadeService;
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorPessoaId(final Long pessoaId) {
            return Optional.empty();
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorSub(final String sub) {
            return provisionamentoIdentidadeService.localizarPessoaPorSub(sub)
                    .map(RegistroDispositivoService.ClienteContextoPessoaPerfilSistemaLegado::paraContexto);
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorEmail(final String email) {
            return Optional.empty();
        }

        private static ContextoPessoaPerfilSistema paraContexto(final Pessoa pessoa) {
            return new ContextoPessoaPerfilSistema(
                    pessoa.getId(),
                    pessoa.getSub(),
                    pessoa.getEmail(),
                    pessoa.getNome(),
                    null,
                    null);
        }
    }
}
