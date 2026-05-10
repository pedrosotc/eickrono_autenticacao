package com.eickrono.api.identidade.apresentacao.dto;

import java.time.OffsetDateTime;

/**
 * DTO que representa o estado de um provedor social suportado.
 */
public record VinculoSocialDto(
        String provedor,
        boolean suportado,
        boolean vinculado,
        OffsetDateTime vinculadoEm,
        String identificadorMascarado,
        String nomeExibicaoExterno,
        String urlAvatarExterno,
        OffsetDateTime avatarExternoAtualizadoEm,
        boolean avatarPrincipalNoProjeto,
        String statusAvatarSocial,
        String mensagemAvatarSocial) {
}
