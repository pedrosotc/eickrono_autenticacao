package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Mantém statusUsuario apenas como compatibilidade de JSON; internamente o contrato já trata statusPerfilSistema.
public record SessaoApiResposta(
        boolean autenticado,
        String tipoToken,
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenDispositivo,
        OffsetDateTime tokenDispositivoExpiraEm,
        UUID registroDispositivoId,
        OffsetDateTime registroDispositivoExpiraEm,
        StatusRegistroDispositivo statusRegistroDispositivo,
        List<CanalVerificacao> canaisConfirmacao,
        @JsonProperty("statusUsuario") String statusPerfilSistema,
        String emailPrincipal,
        boolean primeiraSessao,
        boolean podeOferecerBiometria,
        boolean podeOferecerVinculacaoSocial
) {

    public SessaoApiResposta {
        canaisConfirmacao = canaisConfirmacao == null ? null : List.copyOf(canaisConfirmacao);
    }
}
