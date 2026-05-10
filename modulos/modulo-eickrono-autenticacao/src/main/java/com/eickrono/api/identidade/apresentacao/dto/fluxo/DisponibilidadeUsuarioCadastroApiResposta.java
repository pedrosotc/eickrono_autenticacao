package com.eickrono.api.identidade.apresentacao.dto.fluxo;

public record DisponibilidadeUsuarioCadastroApiResposta(
        String usuario,
        boolean disponivel
) {
}
