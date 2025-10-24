package com.eickrono.api.identidade.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta devolvida após confirmar o registro de um dispositivo.
 *
 * @param tokenDispositivo token opaco enviado ao aplicativo.
 * @param tokenExpiraEm momento em que o token expira.
 * @param registroId identificador do registro confirmado.
 * @param emitidoEm momento de emissão do token.
 */
public record ConfirmacaoRegistroResponse(String tokenDispositivo,
                                          OffsetDateTime tokenExpiraEm,
                                          UUID registroId,
                                          OffsetDateTime emitidoEm) {
}
