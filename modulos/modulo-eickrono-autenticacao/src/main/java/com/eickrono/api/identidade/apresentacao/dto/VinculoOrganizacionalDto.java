package com.eickrono.api.identidade.apresentacao.dto;

import java.time.OffsetDateTime;

/**
 * DTO que representa um vínculo organizacional já materializado para o usuário autenticado.
 */
public record VinculoOrganizacionalDto(
        String organizacaoId,
        String nomeOrganizacao,
        String conviteCodigo,
        String emailConvidado,
        boolean exigeContaSeparada,
        OffsetDateTime vinculadoEm) {
}
