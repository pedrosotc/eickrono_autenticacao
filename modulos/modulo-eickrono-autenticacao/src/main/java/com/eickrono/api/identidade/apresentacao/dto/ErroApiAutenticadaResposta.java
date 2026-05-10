package com.eickrono.api.identidade.apresentacao.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ErroApiAutenticadaResposta(
        String codigo,
        String mensagem,
        Map<String, Object> detalhes
) {

    public ErroApiAutenticadaResposta {
        detalhes = detalhes == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(detalhes));
    }
}
