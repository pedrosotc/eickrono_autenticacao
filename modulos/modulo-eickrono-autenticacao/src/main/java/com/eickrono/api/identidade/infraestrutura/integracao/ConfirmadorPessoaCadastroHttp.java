package com.eickrono.api.identidade.infraestrutura.integracao;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.eickrono.api.identidade.aplicacao.modelo.PessoaCanonicaConfirmada;
import com.eickrono.api.identidade.aplicacao.servico.ConfirmadorPessoaCadastroServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IdentidadeBackchannelProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import java.net.URI;
import java.time.OffsetDateTime;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ConfirmadorPessoaCadastroHttp implements ConfirmadorPessoaCadastroServico {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_CONFIRMACAO_PESSOA = "/identidade/pessoas/interna/confirmacoes-email";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelIdentidadeKeycloak clienteTokenBackchannelIdentidadeKeycloak;

    public ConfirmadorPessoaCadastroHttp(final RestTemplateBuilder restTemplateBuilder,
                                         final IdentidadeBackchannelProperties properties,
                                         final IntegracaoInternaProperties integracaoInternaProperties,
                                         final ConfiguradorRestTemplateBackchannelMtls configuradorRestTemplateBackchannelMtls,
                                         final ClienteTokenBackchannelIdentidadeKeycloak clienteTokenBackchannelIdentidadeKeycloak) {
        IdentidadeBackchannelProperties configuracao = Objects.requireNonNull(properties, "properties é obrigatório");
        this.urlBase = Objects.requireNonNull(configuracao.getUrlBase(), "integracao.identidade.url-base é obrigatório");
        this.restTemplate = Objects.requireNonNull(
                        configuradorRestTemplateBackchannelMtls,
                        "configuradorRestTemplateBackchannelMtls é obrigatório")
                .configurar(restTemplateBuilder, this.urlBase, configuracao.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.segredoInterno = Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório")
                .getSegredo();
        this.clienteTokenBackchannelIdentidadeKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelIdentidadeKeycloak,
                "clienteTokenBackchannelIdentidadeKeycloak é obrigatório");
    }

    @Override
    public PessoaCanonicaConfirmada confirmarEmailCadastro(final String sub,
                                                           final String email,
                                                           final String nomeCompleto,
                                                           final OffsetDateTime confirmadoEm) {
        ResponseEntity<ConfirmacaoPessoaCadastroInternoResponse> response = restTemplate.exchange(
                URI.create(urlBase + CAMINHO_CONFIRMACAO_PESSOA),
                HttpMethod.POST,
                new HttpEntity<>(
                        new ConfirmacaoPessoaCadastroInternoRequest(sub, email, nomeCompleto, confirmadoEm),
                        cabecalhosBasicos()),
                ConfirmacaoPessoaCadastroInternoResponse.class
        );
        ConfirmacaoPessoaCadastroInternoResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.pessoaId() == null) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Nao foi possivel confirmar a pessoa canonica no servico de identidade."
            );
        }
        return new PessoaCanonicaConfirmada(body.pessoaId(), body.sub(), body.emailPrincipal());
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelIdentidadeKeycloak.obterTokenBearer());
        return headers;
    }

    private record ConfirmacaoPessoaCadastroInternoRequest(
            String sub,
            String email,
            String nomeCompleto,
            OffsetDateTime confirmadoEm
    ) {
    }

    private record ConfirmacaoPessoaCadastroInternoResponse(
            Long pessoaId,
            String sub,
            String emailPrincipal
    ) {
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
