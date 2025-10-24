package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Serviço responsável pela emissão e validação de tokens de dispositivos.
 */
@Service
public class TokenDispositivoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenDispositivoService.class);
    private static final String HMAC_ALG = "HmacSHA256";

    private final TokenDispositivoRepositorio tokenRepositorio;
    private final DispositivoProperties dispositivoProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
    private final HexFormat hexFormat = HexFormat.of();

    public TokenDispositivoService(TokenDispositivoRepositorio tokenRepositorio,
                                   DispositivoProperties dispositivoProperties,
                                   Clock clock) {
        this.tokenRepositorio = tokenRepositorio;
        this.dispositivoProperties = dispositivoProperties;
        this.clock = clock;
    }

    @Transactional
    public TokenEmitido emitirToken(RegistroDispositivo registro, String usuarioSub) {
        Objects.requireNonNull(registro, "registro é obrigatório");
        Objects.requireNonNull(usuarioSub, "usuarioSub é obrigatório");

        revogarTokensAtivos(usuarioSub, MotivoRevogacaoToken.NOVO_DISPOSITIVO_CONFIRMANDO);

        OffsetDateTime agora = OffsetDateTime.now(clock);
        OffsetDateTime expiraEm = agora.plusHours(dispositivoProperties.getToken().getValidadeHoras());
        String tokenClaro = gerarTokenClaro();
        String hash = gerarHashToken(tokenClaro);

        TokenDispositivo entidade = new TokenDispositivo(
                UUID.randomUUID(),
                registro,
                usuarioSub,
                registro.getFingerprint(),
                registro.getPlataforma(),
                registro.getVersaoAplicativo().orElse(null),
                hash,
                StatusTokenDispositivo.ATIVO,
                agora,
                expiraEm
        );
        tokenRepositorio.save(entidade);

        LOGGER.info("Token de dispositivo emitido para usuarioSub={} registro={} fingerprint={}",
                usuarioSub, registro.getId(), registro.getFingerprint());

        return new TokenEmitido(tokenClaro, entidade);
    }

    @Transactional
    public void revogarTokensAtivos(String usuarioSub, MotivoRevogacaoToken motivo) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        List<TokenDispositivo> ativos = tokenRepositorio.findByUsuarioSubAndStatus(usuarioSub, StatusTokenDispositivo.ATIVO);
        for (TokenDispositivo token : ativos) {
            token.revogar(motivo, agora);
            LOGGER.info("Token de dispositivo revogado. usuarioSub={} tokenId={} motivo={}",
                    usuarioSub, token.getId(), motivo);
        }
    }

    public Optional<TokenDispositivo> validarTokenAtivo(String usuarioSub, String tokenClaro) {
        if (!StringUtils.hasText(tokenClaro)) {
            return Optional.empty();
        }
        String hash = gerarHashToken(tokenClaro);
        return tokenRepositorio.findByUsuarioSubAndTokenHashAndStatus(usuarioSub, hash, StatusTokenDispositivo.ATIVO)
                .filter(token -> token.estaAtivo(OffsetDateTime.now(clock)));
    }

    private String gerarTokenClaro() {
        byte[] buffer = new byte[dispositivoProperties.getToken().getTamanhoBytes()];
        secureRandom.nextBytes(buffer);
        return base64Encoder.encodeToString(buffer);
    }

    private String gerarHashToken(String tokenClaro) {
        String segredo = dispositivoProperties.getToken().getSegredoHmac();
        if (!StringUtils.hasText(segredo)) {
            throw new IllegalStateException("identidade.dispositivo.token.segredo-hmac deve ser configurado");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] resultado = mac.doFinal(tokenClaro.getBytes(StandardCharsets.UTF_8));
            return hexFormat.formatHex(resultado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gerar hash do token de dispositivo", e);
        }
    }

    public record TokenEmitido(String tokenClaro, TokenDispositivo entidade) {
    }
}
