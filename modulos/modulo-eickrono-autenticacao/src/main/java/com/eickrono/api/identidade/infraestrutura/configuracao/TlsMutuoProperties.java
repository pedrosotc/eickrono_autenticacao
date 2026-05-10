package com.eickrono.api.identidade.infraestrutura.configuracao;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações para mTLS no serviço.
 */
@Validated
@ConfigurationProperties(prefix = "seguranca.mtls")
public class TlsMutuoProperties {

    private final boolean habilitado;
    private final int portaInterna;
    private final String keystoreArquivo;
    private final String keystoreSenha;
    private final String truststoreArquivo;
    private final String truststoreSenha;

    public TlsMutuoProperties(
            @DefaultValue("false") boolean habilitado,
            @DefaultValue("0") int portaInterna,
            String keystoreArquivo,
            String keystoreSenha,
            String truststoreArquivo,
            String truststoreSenha) {
        this.habilitado = habilitado;
        this.portaInterna = portaInterna;
        this.keystoreArquivo = keystoreArquivo;
        this.keystoreSenha = keystoreSenha;
        this.truststoreArquivo = truststoreArquivo;
        this.truststoreSenha = truststoreSenha;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public int getPortaInterna() {
        return portaInterna;
    }

    public String getKeystoreArquivo() {
        return keystoreArquivo;
    }

    public String getKeystoreSenha() {
        return keystoreSenha;
    }

    public String getTruststoreArquivo() {
        return truststoreArquivo;
    }

    public String getTruststoreSenha() {
        return truststoreSenha;
    }
}
