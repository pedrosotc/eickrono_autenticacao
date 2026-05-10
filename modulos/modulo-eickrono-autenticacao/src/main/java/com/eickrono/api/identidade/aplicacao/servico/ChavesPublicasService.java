package com.eickrono.api.identidade.aplicacao.servico;

import java.util.Objects;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Serviço responsável por repassar o JWKS do servidor de autorização.
 */
@Service
public class ChavesPublicasService {

    private static final String CACHE_JWKS = "jwks-cache";

    private final RestTemplate restTemplate;
    private final String jwkSetUri;
    private final Cache cache;

    public ChavesPublicasService(RestTemplateBuilder builder,
                                 OAuth2ResourceServerProperties properties,
                                 CacheManager cacheManager) {
        this.restTemplate = builder.build();
        this.jwkSetUri = Objects.requireNonNull(
                Objects.requireNonNull(
                        Objects.requireNonNull(properties, "properties e obrigatorio").getJwt(),
                        "JWT properties obrigatorias")
                        .getJwkSetUri(),
                "JWK Set URI obrigatoria.");
        this.cache = cacheManager.getCache(CACHE_JWKS);
    }

    public String obterChavesPublicas() {
        if (cache != null) {
            String emCache = cache.get("jwks", String.class);
            if (emCache != null) {
                return emCache;
            }
        }
        String resposta = restTemplate.getForObject(
                Objects.requireNonNull(jwkSetUri, "JWK Set URI obrigatoria."),
                String.class);
        if (resposta == null) {
            throw new IllegalStateException("Resposta JWKS obrigatoria.");
        }
        if (cache != null) {
            cache.put("jwks", resposta);
        }
        return resposta;
    }
}
