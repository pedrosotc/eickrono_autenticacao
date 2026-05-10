package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.aplicacao.excecao.EntregaIntegracaoProdutoException;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.servico.ExecutorPendenciaIntegracaoProdutoService;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

@Component
public class ExecutorPendenciaIntegracaoProdutoHttp implements ExecutorPendenciaIntegracaoProdutoService {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplateBuilder restTemplateBuilder;
    private final ConfiguradorRestTemplateBackchannelMtls configuradorMtls;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    public ExecutorPendenciaIntegracaoProdutoHttp(final RestTemplateBuilder restTemplateBuilder,
                                                  final ConfiguradorRestTemplateBackchannelMtls configuradorMtls,
                                                  final PerfilDominioBackchannelProperties properties,
                                                  final IntegracaoInternaProperties integracaoInternaProperties,
                                                  final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak) {
        this.restTemplateBuilder = Objects.requireNonNull(restTemplateBuilder, "restTemplateBuilder e obrigatorio");
        this.configuradorMtls = Objects.requireNonNull(configuradorMtls, "configuradorMtls e obrigatorio");
        PerfilDominioBackchannelProperties configuracao = Objects.requireNonNull(properties, "properties e obrigatorio");
        this.urlBase = normalizarUrlBase(configuracao.getUrlBase());
        this.segredoInterno = Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties e obrigatorio")
                .getSegredo();
        this.clienteTokenBackchannelPerfilKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelPerfilKeycloak,
                "clienteTokenBackchannelPerfilKeycloak e obrigatorio");
    }

    @Override
    public void entregar(final PendenciaIntegracaoProduto pendencia, final int timeoutEntregaMillis) {
        Objects.requireNonNull(pendencia, "pendencia e obrigatoria");
        HttpMethod metodo = converterMetodo(pendencia.metodoHttp());
        RestTemplate restTemplate = criarRestTemplate(Duration.ofMillis(timeoutEntregaMillis));
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(urlBase + pendencia.uriEndpoint()),
                    metodo,
                    new HttpEntity<>(pendencia.payloadJson(), cabecalhosBasicos()),
                    String.class
            );
            int status = response.getStatusCode().value();
            if (response.getStatusCode().is2xxSuccessful()) {
                return;
            }
            if (status >= 500) {
                throw new EntregaIntegracaoProdutoException(
                        "HTTP_5XX",
                        "Backend do produto retornou erro 5xx durante a entrega.",
                        true
                );
            }
            throw new EntregaIntegracaoProdutoException(
                    "HTTP_4XX",
                    "Backend do produto retornou erro 4xx durante a entrega.",
                    false
            );
        } catch (ResourceAccessException ex) {
            throw new EntregaIntegracaoProdutoException(
                    "TIMEOUT_ENTREGA",
                    "Falha de rede ou timeout durante a entrega ao backend do produto.",
                    true,
                    ex
            );
        } catch (RestClientException ex) {
            throw new EntregaIntegracaoProdutoException(
                    "ERRO_DESCONHECIDO",
                    "Falha inesperada durante a entrega ao backend do produto.",
                    true,
                    ex
            );
        }
    }

    private RestTemplate criarRestTemplate(final Duration timeout) {
        return configuradorMtls
                .configurar(restTemplateBuilder, urlBase, timeout)
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer());
        return headers;
    }

    private static HttpMethod converterMetodo(final String metodoHttp) {
        try {
            return HttpMethod.valueOf(Objects.requireNonNull(metodoHttp, "metodoHttp e obrigatorio"));
        } catch (IllegalArgumentException ex) {
            throw new EntregaIntegracaoProdutoException(
                    "METODO_HTTP_INVALIDO",
                    "Metodo HTTP invalido para entrega ao backend do produto.",
                    false,
                    ex
            );
        }
    }

    private static String normalizarUrlBase(final String urlBase) {
        if (!StringUtils.hasText(urlBase)) {
            throw new IllegalStateException("integracao.perfil.url-base e obrigatorio");
        }
        String valor = urlBase.trim();
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
