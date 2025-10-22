package com.eickrono.api.contas.configuracao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades específicas para validações FAPI desta API.
 */
@Validated
@ConfigurationProperties(prefix = "fapi.seguranca")
public class FapiProperties {

    @NotBlank
    private final String audienciaEsperada;

    @Positive
    private final int toleranciaClockSkewSegundos;

    public FapiProperties(
            @NotBlank String audienciaEsperada,
            @DefaultValue("60") @Positive int toleranciaClockSkewSegundos) {
        this.audienciaEsperada = audienciaEsperada;
        this.toleranciaClockSkewSegundos = toleranciaClockSkewSegundos;
    }

    public String getAudienciaEsperada() {
        return audienciaEsperada;
    }

    public Duration getToleranciaClockSkew() {
        return Duration.ofSeconds(toleranciaClockSkewSegundos);
    }
}
