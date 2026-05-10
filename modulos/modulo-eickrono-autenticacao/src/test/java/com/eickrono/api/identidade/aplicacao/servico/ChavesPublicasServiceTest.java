package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Objects;

class ChavesPublicasServiceTest {

    private static final String JWKS_URI = "https://sso.eickrono.dev/realms/dev/protocol/openid-connect/certs";

    private ChavesPublicasService chavesPublicasService;
    private MockRestServiceServer mockServer;

    /**
     * Monta o serviço original com RestTemplate e cache em memória para controlar as respostas.
     */
    private void inicializarServico() {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setJwkSetUri(JWKS_URI);
        CacheManager cacheManager = new ConcurrentMapCacheManager("jwks-cache");

        chavesPublicasService = new ChavesPublicasService(builder, properties, cacheManager);
        RestTemplate restTemplate = Objects.requireNonNull(
                (RestTemplate) ReflectionTestUtils.getField(chavesPublicasService, "restTemplate"));
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    /**
     * Valida a primeira chamada ao endpoint remoto quando o cache ainda está vazio.
     * Esperamos que a URI configurada seja chamada e o corpo retornado seja propagado.
     */
    @Test
    @DisplayName("deve buscar JWKS remotamente quando cache estiver vazio")
    void deveBuscarRemotamenteQuandoCacheVazio() {
        inicializarServico();
        mockServer.expect(requestTo(JWKS_URI))
                .andRespond(withSuccess("{\"keys\":[]}", MediaType.APPLICATION_JSON));

        String resposta = chavesPublicasService.obterChavesPublicas();

        assertThat(resposta).isEqualTo("{\"keys\":[]}");
        mockServer.verify();
    }

    /**
     * Após a primeira consulta, o valor deve ficar armazenado e ser reutilizado sem novo request.
     */
    @Test
    @DisplayName("deve reutilizar valor armazenado em cache")
    void deveUsarCacheQuandoDisponivel() {
        inicializarServico();
        mockServer.expect(requestTo(JWKS_URI))
                .andRespond(withSuccess("{\"keys\":[1]}", MediaType.APPLICATION_JSON));

        String primeira = chavesPublicasService.obterChavesPublicas();
        assertThat(primeira).isEqualTo("{\"keys\":[1]}");
        mockServer.verify();

        String segunda = chavesPublicasService.obterChavesPublicas();
        assertThat(segunda).isEqualTo("{\"keys\":[1]}");
    }
}
