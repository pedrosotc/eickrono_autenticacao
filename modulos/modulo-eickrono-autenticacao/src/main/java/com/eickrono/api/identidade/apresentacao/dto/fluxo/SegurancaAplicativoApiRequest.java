package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SegurancaAplicativoApiRequest(
        @NotBlank String plataforma,
        @NotBlank String provedorAtestacao,
        boolean rootOuJailbreak,
        boolean debuggerDetectado,
        boolean hookingSuspeito,
        boolean tamperSuspeito,
        boolean riscoCapturaTela,
        boolean assinaturaValida,
        boolean identidadeAplicativoValida,
        List<String> sinaisRisco,
        int scoreRiscoLocal,
        String packageName,
        String bundleIdentifier,
        String teamIdentifier,
        String assinaturaSha256
) {
}
