package com.eickrono.api.identidade.apresentacao.dto;

import java.time.OffsetDateTime;

public record RegistroDispositivoSessaoResponse(
        String tokenDispositivo,
        OffsetDateTime tokenDispositivoExpiraEm
) {
}
