package com.eickrono.api.identidade.apresentacao.dto;

import java.time.OffsetDateTime;

/**
 * Payload padronizado para informar o estado de um token de dispositivo.
 */
public record ValidacaoTokenDispositivoResponse(
        boolean valido,
        String codigo,
        String mensagem,
        OffsetDateTime expiraEm) {
}
