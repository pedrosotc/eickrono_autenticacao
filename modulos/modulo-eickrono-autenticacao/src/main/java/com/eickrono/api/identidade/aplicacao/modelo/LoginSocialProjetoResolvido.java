package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.Objects;
import java.util.UUID;

public record LoginSocialProjetoResolvido(
        UUID usuarioId,
        String subRemoto
) {

    public LoginSocialProjetoResolvido {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(subRemoto, "subRemoto é obrigatório");
    }
}
