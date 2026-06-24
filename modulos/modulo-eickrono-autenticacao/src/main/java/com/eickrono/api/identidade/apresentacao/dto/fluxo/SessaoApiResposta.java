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
        String sub,
        String usuario,
        String avatarPreferidoUrl,
        String avatarPreferidoOrigem,
        String avatarPreferidoVersao,
        OffsetDateTime avatarPreferidoAtualizadoEm,
        boolean primeiraSessao,
        boolean podeOferecerBiometria,
        boolean podeOferecerVinculacaoSocial
) {

    public SessaoApiResposta(final boolean autenticado,
                             final String tipoToken,
                             final String accessToken,
                             final String refreshToken,
                             final long expiresIn,
                             final String tokenDispositivo,
                             final OffsetDateTime tokenDispositivoExpiraEm,
                             final UUID registroDispositivoId,
                             final OffsetDateTime registroDispositivoExpiraEm,
                             final StatusRegistroDispositivo statusRegistroDispositivo,
                             final List<CanalVerificacao> canaisConfirmacao,
                             final String statusPerfilSistema,
                             final String emailPrincipal,
                             final boolean primeiraSessao,
                             final boolean podeOferecerBiometria,
                             final boolean podeOferecerVinculacaoSocial) {
        this(
                autenticado,
                tipoToken,
                accessToken,
                refreshToken,
                expiresIn,
                tokenDispositivo,
                tokenDispositivoExpiraEm,
                registroDispositivoId,
                registroDispositivoExpiraEm,
                statusRegistroDispositivo,
                canaisConfirmacao,
                statusPerfilSistema,
                emailPrincipal,
                null,
                null,
                null,
                null,
                null,
                null,
                primeiraSessao,
                podeOferecerBiometria,
                podeOferecerVinculacaoSocial
        );
    }

    public SessaoApiResposta(final boolean autenticado,
                             final String tipoToken,
                             final String accessToken,
                             final String refreshToken,
                             final long expiresIn,
                             final String tokenDispositivo,
                             final OffsetDateTime tokenDispositivoExpiraEm,
                             final UUID registroDispositivoId,
                             final OffsetDateTime registroDispositivoExpiraEm,
                             final StatusRegistroDispositivo statusRegistroDispositivo,
                             final List<CanalVerificacao> canaisConfirmacao,
                             final String statusPerfilSistema,
                             final String emailPrincipal,
                             final String usuario,
                             final boolean primeiraSessao,
                             final boolean podeOferecerBiometria,
                             final boolean podeOferecerVinculacaoSocial) {
        this(
                autenticado,
                tipoToken,
                accessToken,
                refreshToken,
                expiresIn,
                tokenDispositivo,
                tokenDispositivoExpiraEm,
                registroDispositivoId,
                registroDispositivoExpiraEm,
                statusRegistroDispositivo,
                canaisConfirmacao,
                statusPerfilSistema,
                emailPrincipal,
                null,
                usuario,
                null,
                null,
                null,
                null,
                primeiraSessao,
                podeOferecerBiometria,
                podeOferecerVinculacaoSocial
        );
    }

    public SessaoApiResposta {
        canaisConfirmacao = canaisConfirmacao == null ? null : List.copyOf(canaisConfirmacao);
    }
}
