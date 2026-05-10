package com.eickrono.api.identidade.apresentacao.dto;

import java.util.List;

/**
 * Resposta da listagem dos vínculos organizacionais do usuário autenticado.
 */
public record VinculosOrganizacionaisDto(List<VinculoOrganizacionalDto> vinculos) {
}
