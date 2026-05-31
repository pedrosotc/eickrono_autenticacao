package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identidade.social.credenciais")
public class CredenciaisSociaisNativasProperties {

    private Duration timeout = Duration.ofSeconds(5);
    private final Google google = new Google();
    private final Apple apple = new Apple();

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }

    public Google getGoogle() {
        return google;
    }

    public Apple getApple() {
        return apple;
    }

    public static final class Google {

        private String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";

        public String getUserInfoUrl() {
            return userInfoUrl;
        }

        public void setUserInfoUrl(final String userInfoUrl) {
            this.userInfoUrl = userInfoUrl;
        }
    }

    public static final class Apple {

        private String issuer = "https://appleid.apple.com";
        private String jwkSetUri = "https://appleid.apple.com/auth/keys";
        private List<String> audiences = new ArrayList<>();

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(final String issuer) {
            this.issuer = issuer;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(final String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public List<String> getAudiences() {
            return audiences;
        }

        public void setAudiences(final List<String> audiences) {
            this.audiences = audiences == null ? new ArrayList<>() : new ArrayList<>(audiences);
        }
    }
}
