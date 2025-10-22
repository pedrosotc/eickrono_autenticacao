package com.eickrono.api.identidade.servico;

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
    private final OAuth2ResourceServerProperties properties;
    private final Cache cache;

    public ChavesPublicasService(RestTemplateBuilder builder,
                                 OAuth2ResourceServerProperties properties,
                                 CacheManager cacheManager) {
        this.restTemplate = builder.build();
        this.properties = properties;
        this.cache = cacheManager.getCache(CACHE_JWKS);
    }

    public String obterChavesPublicas() {
        if (cache != null) {
            String emCache = cache.get("jwks", String.class);
            if (emCache != null) {
                return emCache;
            }
        }
        String resposta = restTemplate.getForObject(properties.getJwt().getJwkSetUri(), String.class);
        if (cache != null && resposta != null) {
            cache.put("jwks", resposta);
        }
        return resposta;
    }
}
