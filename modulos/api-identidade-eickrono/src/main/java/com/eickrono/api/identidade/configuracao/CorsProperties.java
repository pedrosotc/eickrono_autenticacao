package com.eickrono.api.identidade.configuracao;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações simples de CORS com lista de origens permitidas.
 */
@ConfigurationProperties(prefix = "seguranca.cors")
public class CorsProperties {

    private final List<String> origensPermitidas;

    public CorsProperties(@DefaultValue("http://localhost") List<String> origensPermitidas) {
        this.origensPermitidas = List.copyOf(origensPermitidas);
    }

    public List<String> getOrigensPermitidas() {
        return origensPermitidas;
    }
}
