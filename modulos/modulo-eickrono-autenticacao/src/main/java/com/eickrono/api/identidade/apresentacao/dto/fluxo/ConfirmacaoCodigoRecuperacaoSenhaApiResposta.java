package com.eickrono.api.identidade.apresentacao.dto.fluxo;

public record ConfirmacaoCodigoRecuperacaoSenhaApiResposta(
        String fluxoId,
        boolean codigoConfirmado,
        boolean podeDefinirSenha
) {
}
