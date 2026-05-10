package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

final class FingerprintDispositivoUtil {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private FingerprintDispositivoUtil() {
    }

    static String derivar(final DispositivoSessaoApiRequest dispositivo,
                          final DispositivoProperties dispositivoProperties) {
        String base = String.join("|",
                normalizarObrigatorio(dispositivo.plataforma(), "plataforma").toUpperCase(Locale.ROOT),
                normalizarOpcional(dispositivo.identificadorInstalacao()),
                normalizarOpcional(dispositivo.modelo()),
                normalizarOpcional(dispositivo.fabricante()),
                normalizarOpcional(dispositivo.sistemaOperacional()),
                normalizarOpcional(dispositivo.versaoSistema()),
                normalizarOpcional(dispositivo.versaoApp())
        );
        String segredo = dispositivoProperties.getToken().getSegredoHmac();
        if (!StringUtils.hasText(segredo)) {
            throw new IllegalStateException("identidade.dispositivo.token.segredo-hmac deve ser configurado");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] resultado = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao derivar fingerprint do dispositivo", ex);
        }
    }

    private static String normalizarObrigatorio(final String valor, final String campo) {
        String normalizado = normalizarOpcional(valor);
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return normalizado;
    }

    private static String normalizarOpcional(final String valor) {
        return valor == null ? "" : valor.trim();
    }
}
