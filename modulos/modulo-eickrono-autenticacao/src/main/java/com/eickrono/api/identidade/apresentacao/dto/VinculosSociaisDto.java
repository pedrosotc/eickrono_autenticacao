package com.eickrono.api.identidade.apresentacao.dto;

import java.util.List;

/**
 * Resposta da listagem de vínculos sociais suportados.
 */
public record VinculosSociaisDto(
        List<VinculoSocialDto> provedores,
        String avatarPreferidoOrigem,
        String avatarPreferidoUrl
) {
}
