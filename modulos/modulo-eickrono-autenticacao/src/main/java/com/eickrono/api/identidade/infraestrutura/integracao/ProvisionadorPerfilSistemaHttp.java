package com.eickrono.api.identidade.infraestrutura.integracao;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

import com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado;
import com.eickrono.api.identidade.aplicacao.servico.ProvisionadorPerfilSistemaServico;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import java.net.URI;
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
public class ProvisionadorPerfilSistemaHttp implements ProvisionadorPerfilSistemaServico {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_PROVISIONAMENTO = "/api/interna/perfis-sistema/provisionamentos";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    public ProvisionadorPerfilSistemaHttp(final RestTemplateBuilder restTemplateBuilder,
                                          final PerfilDominioBackchannelProperties properties,
                                          final IntegracaoInternaProperties integracaoInternaProperties,
                                          final ConfiguradorRestTemplateBackchannelMtls configuradorRestTemplateBackchannelMtls,
                                          final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak) {
        PerfilDominioBackchannelProperties configuracao = Objects.requireNonNull(properties, "properties e obrigatorio");
        this.urlBase = Objects.requireNonNull(configuracao.getUrlBase(), "integracao.perfil.url-base e obrigatorio");
        this.restTemplate = Objects.requireNonNull(
                        configuradorRestTemplateBackchannelMtls,
                        "configuradorRestTemplateBackchannelMtls e obrigatorio")
                .configurar(restTemplateBuilder, this.urlBase, configuracao.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.segredoInterno = Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties e obrigatorio")
                .getSegredo();
        this.clienteTokenBackchannelPerfilKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelPerfilKeycloak,
                "clienteTokenBackchannelPerfilKeycloak e obrigatorio");
    }

    @Override
    public ProvisionamentoPerfilSistemaRealizado provisionarCadastroConfirmado(final CadastroConta cadastroConta,
                                                                               final Long pessoaIdCentral) {
        Objects.requireNonNull(cadastroConta, "cadastroConta e obrigatorio");
        Objects.requireNonNull(pessoaIdCentral, "pessoaIdCentral e obrigatorio");
        ResponseEntity<ProvisionamentoPerfilSistemaInternoResponse> response;
        try {
            response = restTemplate.exchange(
                    URI.create(urlBase + CAMINHO_PROVISIONAMENTO),
                    HttpMethod.POST,
                    new HttpEntity<>(
                            ProvisionamentoPerfilSistemaProdutoRequestPayload.fromCadastro(cadastroConta, pessoaIdCentral),
                            cabecalhosBasicos()
                    ),
                    ProvisionamentoPerfilSistemaInternoResponse.class
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Nao foi possivel provisionar o perfil no dominio do thimisu.",
                    ex
            );
        }
        if (response.getStatusCode().value() == CONFLICT.value()) {
            throw new ResponseStatusException(CONFLICT, "O perfil local do thimisu entrou em conflito durante o provisionamento.");
        }
        ProvisionamentoPerfilSistemaInternoResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Nao foi possivel provisionar o perfil no dominio do thimisu."
            );
        }
        return new ProvisionamentoPerfilSistemaRealizado(
                body.perfilSistemaId(),
                body.statusPerfilSistema()
        );
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer());
        return headers;
    }

    private record ProvisionamentoPerfilSistemaInternoResponse(
            String perfilSistemaId,
            String statusPerfilSistema
    ) {
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
