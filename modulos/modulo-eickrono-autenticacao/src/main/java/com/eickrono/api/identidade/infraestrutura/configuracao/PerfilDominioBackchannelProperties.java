package com.eickrono.api.identidade.infraestrutura.configuracao;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integracao.perfil")
public class PerfilDominioBackchannelProperties {

    private String urlBase = "http://localhost:8082";
    private Duration timeout = Duration.ofSeconds(5);
    private final JwtInterno jwtInterno = new JwtInterno();

    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(final String urlBase) {
        this.urlBase = urlBase;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Spring Boot usa a instancia aninhada para bind incremental das propriedades jwtInterno."
    )
    public JwtInterno getJwtInterno() {
        return jwtInterno;
    }

    public static class JwtInterno {

        private String urlBase = "http://localhost:8080";
        private String realm = "eickrono";
        private String clientId = "eickrono-autenticacao-interno";
        private String clientSecret = "CHANGE_ME";
        private Duration timeout = Duration.ofSeconds(5);

        public String getUrlBase() {
            return urlBase;
        }

        public void setUrlBase(final String urlBase) {
            this.urlBase = urlBase;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(final String realm) {
            this.realm = realm;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(final String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(final String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(final Duration timeout) {
            this.timeout = timeout;
        }
    }
}
