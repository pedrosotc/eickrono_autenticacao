package com.eickrono.api.identidade.dto;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * DTO de perfil de identidade.
 */
public record PerfilDto(
        String sub,
        String email,
        String nome,
        Set<String> perfis,
        Set<String> papeis,
        OffsetDateTime atualizadoEm) {

    public PerfilDto {
        perfis = perfis == null ? Set.of() : Set.copyOf(perfis);
        papeis = papeis == null ? Set.of() : Set.copyOf(papeis);
    }
}
