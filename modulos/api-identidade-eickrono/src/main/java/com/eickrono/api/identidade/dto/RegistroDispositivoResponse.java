package com.eickrono.api.identidade.dto;

import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta enviada após registrar um dispositivo.
 */
public record RegistroDispositivoResponse(UUID registroId,
                                          OffsetDateTime expiraEm,
                                          StatusRegistroDispositivo status) {
}
