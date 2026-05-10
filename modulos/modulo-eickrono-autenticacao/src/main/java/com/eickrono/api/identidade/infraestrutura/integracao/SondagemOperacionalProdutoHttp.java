package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.aplicacao.servico.SondagemOperacionalProdutoService;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class SondagemOperacionalProdutoHttp implements SondagemOperacionalProdutoService {

    private static final String CAMINHO_ESTADO = "/api/v1/estado";

    private final RestTemplateBuilder restTemplateBuilder;
    private final ConfiguradorRestTemplateBackchannelMtls configuradorMtls;
    private final String urlBase;

    public SondagemOperacionalProdutoHttp(final RestTemplateBuilder restTemplateBuilder,
                                          final ConfiguradorRestTemplateBackchannelMtls configuradorMtls,
                                          final PerfilDominioBackchannelProperties properties) {
        this.restTemplateBuilder = Objects.requireNonNull(restTemplateBuilder, "restTemplateBuilder e obrigatorio");
        this.configuradorMtls = Objects.requireNonNull(configuradorMtls, "configuradorMtls e obrigatorio");
        PerfilDominioBackchannelProperties configuracao = Objects.requireNonNull(properties, "properties e obrigatorio");
        this.urlBase = normalizarUrlBase(configuracao.getUrlBase());
    }

    @Override
    public boolean produtoDisponivel(final int timeoutMillis) {
        RestTemplate restTemplate = configuradorMtls
                .configurar(restTemplateBuilder, urlBase, Duration.ofMillis(timeoutMillis))
                .build();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(URI.create(urlBase + CAMINHO_ESTADO), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            return false;
        }
    }

    private static String normalizarUrlBase(final String urlBase) {
        if (!StringUtils.hasText(urlBase)) {
            throw new IllegalStateException("integracao.perfil.url-base e obrigatorio");
        }
        String valor = urlBase.trim();
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }
}
