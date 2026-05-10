package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identidade.sessao.interna.keycloak")
public class SessaoInternaKeycloakProperties {

    private String urlBase = "http://localhost:8080";
    private String realm = "eickrono";
    private String clientId = "app-flutter-local";
    private String clientSecret = "";
    private String scope = "";
    private String tokenExchangeClientId = "";
    private String tokenExchangeClientSecret = "";
    private String tokenExchangeAudience = "";
    private String tokenExchangeScope = "";
    private String passwordPepper = "";
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

    public String getScope() {
        return scope;
    }

    public void setScope(final String scope) {
        this.scope = scope;
    }

    public String getTokenExchangeClientId() {
        return tokenExchangeClientId;
    }

    public void setTokenExchangeClientId(final String tokenExchangeClientId) {
        this.tokenExchangeClientId = tokenExchangeClientId;
    }

    public String getTokenExchangeClientSecret() {
        return tokenExchangeClientSecret;
    }

    public void setTokenExchangeClientSecret(final String tokenExchangeClientSecret) {
        this.tokenExchangeClientSecret = tokenExchangeClientSecret;
    }

    public String getTokenExchangeAudience() {
        return tokenExchangeAudience;
    }

    public void setTokenExchangeAudience(final String tokenExchangeAudience) {
        this.tokenExchangeAudience = tokenExchangeAudience;
    }

    public String getTokenExchangeScope() {
        return tokenExchangeScope;
    }

    public void setTokenExchangeScope(final String tokenExchangeScope) {
        this.tokenExchangeScope = tokenExchangeScope;
    }

    public String getPasswordPepper() {
        return passwordPepper;
    }

    public void setPasswordPepper(final String passwordPepper) {
        this.passwordPepper = passwordPepper;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }
}
