package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorExclusaoCadastroProdutoService;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import java.net.URI;
import java.util.List;
import java.util.Locale;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ResolvedorExclusaoCadastroProdutoThimisuHttp implements ResolvedorExclusaoCadastroProdutoService {

    private static final String PRODUTO_THIMISU = "THIMISU";
    private static final String SISTEMA_THIMISU = "EICKRONO_THIMISU_BACKEND";
    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_DRY_RUN =
            "/api/interna/perfis-sistema/exclusoes-cadastro-produto/dry-run";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    public ResolvedorExclusaoCadastroProdutoThimisuHttp(
            final RestTemplateBuilder restTemplateBuilder,
            final PerfilDominioBackchannelProperties properties,
            final IntegracaoInternaProperties integracaoInternaProperties,
            final ConfiguradorRestTemplateBackchannelMtls configuradorRestTemplateBackchannelMtls,
            final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak) {
        PerfilDominioBackchannelProperties configuracao = Objects.requireNonNull(
                properties,
                "properties e obrigatorio"
        );
        this.urlBase = normalizarUrlBase(configuracao.getUrlBase());
        this.restTemplate = Objects.requireNonNull(
                        configuradorRestTemplateBackchannelMtls,
                        "configuradorRestTemplateBackchannelMtls e obrigatorio")
                .configurar(restTemplateBuilder, this.urlBase, configuracao.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.segredoInterno = Objects.requireNonNull(
                integracaoInternaProperties,
                "integracaoInternaProperties e obrigatorio"
        ).getSegredo();
        this.clienteTokenBackchannelPerfilKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelPerfilKeycloak,
                "clienteTokenBackchannelPerfilKeycloak e obrigatorio"
        );
    }

    @Override
    public boolean suporta(final String produto) {
        return PRODUTO_THIMISU.equals(produto == null ? null : produto.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public Resultado simular(final String usuarioPublicoProduto, final String perfilProdutoId) {
        try {
            ResponseEntity<RespostaProduto> response = restTemplate.exchange(
                    URI.create(urlBase + CAMINHO_DRY_RUN),
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new RequisicaoProduto(usuarioPublicoProduto, perfilProdutoId),
                            cabecalhosBasicos()
                    ),
                    RespostaProduto.class
            );
            RespostaProduto body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                return produtoIndisponivel("Backend Thimisu nao retornou dryRun valido.");
            }
            return new Resultado(
                    body.acoes() == null ? List.of() : body.acoes(),
                    body.preservados() == null ? List.of() : body.preservados(),
                    body.bloqueios() == null ? List.of() : body.bloqueios()
            );
        } catch (RestClientException ex) {
            return produtoIndisponivel("Falha ao consultar dryRun interno do backend Thimisu.");
        }
    }

    private Resultado produtoIndisponivel(final String detalhe) {
        return new Resultado(
                List.of(),
                List.of(),
                List.of(new BloqueioExclusaoCadastroProdutoApiResposta(
                        SISTEMA_THIMISU,
                        "produto_dryrun_indisponivel",
                        detalhe
                ))
        );
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer());
        return headers;
    }

    private static String normalizarUrlBase(final String urlBase) {
        if (!StringUtils.hasText(urlBase)) {
            throw new IllegalStateException("integracao.perfil.url-base e obrigatorio");
        }
        String valor = urlBase.trim();
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    private record RequisicaoProduto(
            String usuarioPublicoProduto,
            String perfilProdutoId
    ) {
    }

    private record RespostaProduto(
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
            List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios
    ) {
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
