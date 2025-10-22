package com.eickrono.api.contas.configuracao;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriedades de segurança aplicadas ao Swagger em homologação.
 */
@ConfigurationProperties(prefix = "documentacao.swagger")
public class SwaggerSegurancaProperties {

    private final String usuario;
    private final String senha;
    private final List<String> ipsPermitidos;

    public SwaggerSegurancaProperties(String usuario,
                                      String senha,
                                      @DefaultValue("127.0.0.1") List<String> ipsPermitidos) {
        this.usuario = usuario;
        this.senha = senha;
        this.ipsPermitidos = List.copyOf(ipsPermitidos);
    }

    public String getUsuario() {
        return usuario;
    }

    public String getSenha() {
        return senha;
    }

    public List<String> getIpsPermitidos() {
        return ipsPermitidos;
    }
}
