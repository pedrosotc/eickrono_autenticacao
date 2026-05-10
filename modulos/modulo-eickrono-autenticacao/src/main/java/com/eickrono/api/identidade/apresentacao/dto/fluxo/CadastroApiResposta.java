package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import com.fasterxml.jackson.annotation.JsonProperty;

// Mantém usuarioId/statusUsuario apenas como compatibilidade de JSON; internamente o contrato já trata perfilSistema.
public record CadastroApiResposta(
        String cadastroId,
        @JsonProperty("usuarioId") String perfilSistemaId,
        @JsonProperty("statusUsuario") String statusPerfilSistema,
        String emailPrincipal,
        String telefonePrincipal,
        boolean verificacaoEmailObrigatoria,
        String proximoPasso
) {
}
