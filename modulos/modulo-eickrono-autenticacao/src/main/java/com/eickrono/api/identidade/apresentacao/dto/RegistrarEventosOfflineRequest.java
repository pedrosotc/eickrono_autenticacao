package com.eickrono.api.identidade.apresentacao.dto;

import java.util.List;

/**
 * Payload de reconciliação de eventos offline.
 */
public record RegistrarEventosOfflineRequest(List<EventoOfflineDispositivoRequest> eventos) {
    public RegistrarEventosOfflineRequest {
        eventos = eventos == null ? List.of() : List.copyOf(eventos);
    }
}
