package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identidade.cadastro.interna.keycloak")
public class CadastroInternoKeycloakProperties {

    private String urlBase = "http://localhost:8080";
    private String adminRealm = "master";
    private String realm = "eickrono";
    private String clientId = "admin-cli";
    private String clientSecret;
    private String adminUsername = "admin";
    private String adminPassword = "admin";
    private String passwordPepper;
    private Duration timeout = Duration.ofSeconds(5);

    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(final String urlBase) {
        this.urlBase = urlBase;
    }

    public String getAdminRealm() {
        return adminRealm;
    }

    public void setAdminRealm(final String adminRealm) {
        this.adminRealm = adminRealm;
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

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(final String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(final String adminPassword) {
        this.adminPassword = adminPassword;
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
