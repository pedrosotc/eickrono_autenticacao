package com.eickrono.api.identidade.apresentacao.dto.cadastro;

public record DisponibilidadePerfilSistemaInternaApiResposta(
        String identificadorPublicoSistema,
        boolean disponivel
) {
}
