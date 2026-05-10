package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.UUID;

public record ConfirmacaoCodigoRecuperacaoSenhaRealizada(
        UUID fluxoId,
        boolean codigoConfirmado,
        boolean podeDefinirSenha
) {
}
