package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ValidacaoOficialAtestacaoAppResultado(
        boolean executada,
        String resumo,
        String appRecognitionVerdict,
        String appLicensingVerdict,
        List<String> deviceRecognitionVerdict
) {

    public ValidacaoOficialAtestacaoAppResultado {
        deviceRecognitionVerdict = deviceRecognitionVerdict == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(deviceRecognitionVerdict));
    }

    public static ValidacaoOficialAtestacaoAppResultado naoExecutada(final String resumo) {
        return new ValidacaoOficialAtestacaoAppResultado(false, resumo, null, null, List.of());
    }
}
