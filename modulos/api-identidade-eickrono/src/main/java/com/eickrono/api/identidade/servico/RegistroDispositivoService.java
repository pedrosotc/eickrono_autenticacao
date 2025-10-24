package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusCodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.CodigoVerificacaoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.RegistroDispositivoRepositorio;
import com.eickrono.api.identidade.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.dto.ReenvioCodigoRequest;
import com.eickrono.api.identidade.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.dto.RegistroDispositivoResponse;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orquestra o ciclo de vida do registro e confirmação de dispositivos móveis.
 */
@Service
public class RegistroDispositivoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroDispositivoService.class);
    private static final String HMAC_ALG = "HmacSHA256";

    private final RegistroDispositivoRepositorio registroRepositorio;
    private final CodigoVerificacaoRepositorio codigoRepositorio;
    private final TokenDispositivoService tokenDispositivoService;
    private final DispositivoProperties propriedades;
    private final AuditoriaService auditoriaService;
    private final Map<CanalVerificacao, CanalEnvioCodigo> canaisEnvio;
    private final Clock clock;
    private final HexFormat hexFormat = HexFormat.of();

    public RegistroDispositivoService(RegistroDispositivoRepositorio registroRepositorio,
                                      CodigoVerificacaoRepositorio codigoRepositorio,
                                      TokenDispositivoService tokenDispositivoService,
                                      DispositivoProperties propriedades,
                                      AuditoriaService auditoriaService,
                                      List<CanalEnvioCodigo> canaisEnvio,
                                      Clock clock) {
        this.registroRepositorio = registroRepositorio;
        this.codigoRepositorio = codigoRepositorio;
        this.tokenDispositivoService = tokenDispositivoService;
        this.propriedades = propriedades;
        this.auditoriaService = auditoriaService;
        this.canaisEnvio = construirMapaCanais(canaisEnvio);
        this.clock = clock;
    }

    @Transactional
    public RegistroDispositivoResponse solicitarRegistro(RegistroDispositivoRequest request, Optional<String> usuarioSubOpt) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        OffsetDateTime expiraEm = agora.plusHours(propriedades.getCodigo().getExpiracaoHoras());
        UUID id = UUID.randomUUID();
        String emailNormalizado = request.getEmail().trim().toLowerCase();
        RegistroDispositivo registro = new RegistroDispositivo(
                id,
                usuarioSubOpt.orElse(null),
                emailNormalizado,
                request.getTelefone().trim(),
                request.getFingerprint().trim(),
                request.getPlataforma().trim(),
                request.getVersaoAplicativo().trim(),
                request.getChavePublica(),
                StatusRegistroDispositivo.PENDENTE,
                agora,
                expiraEm
        );

        CodigoGerado codigoSms = gerarCodigo(registro, CanalVerificacao.SMS, request.getTelefone().trim(), agora, expiraEm);
        CodigoGerado codigoEmail = gerarCodigo(registro, CanalVerificacao.EMAIL, emailNormalizado, agora, expiraEm);
        registro.adicionarCodigo(codigoSms.entidade());
        registro.adicionarCodigo(codigoEmail.entidade());

        registroRepositorio.save(registro);

        enviarCodigo(codigoSms);
        enviarCodigo(codigoEmail);

        auditoriaService.registrarEvento("DISPOSITIVO_REGISTRO_SOLICITADO",
                usuarioSubOpt.orElse(emailNormalizado),
                "Registro de dispositivo iniciado");

        LOGGER.info("Registro de dispositivo criado id={} email={} fingerprint={}", id, emailNormalizado, request.getFingerprint());

        return new RegistroDispositivoResponse(id, expiraEm, StatusRegistroDispositivo.PENDENTE);
    }

    @Transactional
    public ConfirmacaoRegistroResponse confirmarRegistro(UUID id,
                                                         ConfirmacaoRegistroRequest request,
                                                         Optional<String> usuarioSubOpt) {
        RegistroDispositivo registro = registroRepositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
        OffsetDateTime agora = OffsetDateTime.now(clock);

        if (registro.getStatus() != StatusRegistroDispositivo.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro não está pendente");
        }
        if (registro.getExpiraEm().isBefore(agora)) {
            registro.definirStatus(StatusRegistroDispositivo.EXPIRADO, agora);
            throw new ResponseStatusException(HttpStatus.GONE, "Registro expirado");
        }

        CodigoVerificacao codigoSms = registro.codigoPorCanal(CanalVerificacao.SMS)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Código SMS ausente"));
        CodigoVerificacao codigoEmail = registro.codigoPorCanal(CanalVerificacao.EMAIL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Código e-mail ausente"));

        validarCodigo(codigoSms, request.getCodigoSms(), agora);
        validarCodigo(codigoEmail, request.getCodigoEmail(), agora);

        String usuarioSub = registro.getUsuarioSub()
                .or(() -> usuarioSubOpt)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Usuário não identificado para emissão de token"));
        registro.definirUsuarioSub(usuarioSub);
        registro.definirStatus(StatusRegistroDispositivo.CONFIRMADO, agora);

        ConfirmacaoRegistroResponse response = emitirToken(registro, usuarioSub, agora);

        auditoriaService.registrarEvento("DISPOSITIVO_VERIFICACAO_SUCESSO",
                usuarioSub,
                "Registro de dispositivo confirmado");

        LOGGER.info("Registro de dispositivo confirmado id={} usuarioSub={}", id, usuarioSub);

        return response;
    }

    @Transactional
    public void reenviarCodigos(UUID id, ReenvioCodigoRequest request) {
        RegistroDispositivo registro = registroRepositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (registro.getStatus() != StatusRegistroDispositivo.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro não está pendente");
        }
        if (registro.getExpiraEm().isBefore(agora)) {
            registro.definirStatus(StatusRegistroDispositivo.EXPIRADO, agora);
            throw new ResponseStatusException(HttpStatus.GONE, "Registro expirado");
        }

        boolean reenviou = false;
        if (request.deveReenviarSms()) {
            reenviou |= reenviarCodigo(registro, CanalVerificacao.SMS, registro.getTelefone(), agora);
        }
        if (request.deveReenviarEmail()) {
            reenviou |= reenviarCodigo(registro, CanalVerificacao.EMAIL, registro.getEmail(), agora);
        }

        if (!reenviou) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de reenvios atingido");
        }

        registro.incrementarReenvios();

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
                    auditoriaService.registrarEvento("DISPOSITIVO_TOKEN_REVOGADO",
                            usuarioSub,
                            "Token revogado pelo cliente");
                }, () -> LOGGER.warn("Tentativa de revogar token inexistente usuarioSub={}", usuarioSub));
    }

    private ConfirmacaoRegistroResponse emitirToken(RegistroDispositivo registro, String usuarioSub, OffsetDateTime agora) {
        TokenDispositivoService.TokenEmitido tokenEmitido = tokenDispositivoService.emitirToken(registro, usuarioSub);
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
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder builder = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            builder.append(random.nextInt(0, 10));
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

    private record CodigoGerado(CodigoVerificacao entidade, String codigoClaro) {
    }
}
