package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.aplicacao.servico.MaterializadorPendenciaRemocaoAvatarService;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IdentidadeBackchannelProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class MaterializadorPendenciaRemocaoAvatarIdentidadeHttp
        implements MaterializadorPendenciaRemocaoAvatarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterializadorPendenciaRemocaoAvatarIdentidadeHttp.class);

    private static final String SISTEMA_IDENTIDADE = "EICKRONO_IDENTIDADE_SERVIDOR";
    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_PENDENCIAS =
            "/identidade/avatares/interna/remocoes/pendencias";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelIdentidadeKeycloak clienteTokenBackchannelIdentidadeKeycloak;

    public MaterializadorPendenciaRemocaoAvatarIdentidadeHttp(
            final RestTemplateBuilder restTemplateBuilder,
            final IdentidadeBackchannelProperties properties,
            final IntegracaoInternaProperties integracaoInternaProperties,
            final ConfiguradorRestTemplateBackchannelMtls configuradorRestTemplateBackchannelMtls,
            final ClienteTokenBackchannelIdentidadeKeycloak clienteTokenBackchannelIdentidadeKeycloak) {
        IdentidadeBackchannelProperties configuracao = Objects.requireNonNull(
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
        this.clienteTokenBackchannelIdentidadeKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelIdentidadeKeycloak,
                "clienteTokenBackchannelIdentidadeKeycloak e obrigatorio"
        );
    }

    @Override
    public Resultado materializar(final String correlacaoId,
                                  final String produto,
                                  final List<String> vinculosProdutoIds) {
        try {
            LOGGER.info(
                    "exclusao_cadastro_produto_avatar_pendencia_http_iniciada correlacaoId={} produto={} vinculos={} url={}",
                    correlacaoId,
                    produto,
                    vinculosProdutoIds == null ? 0 : vinculosProdutoIds.size(),
                    urlBase + CAMINHO_PENDENCIAS
            );
            ResponseEntity<RespostaIdentidade> response = restTemplate.exchange(
                    URI.create(urlBase + CAMINHO_PENDENCIAS),
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new RequisicaoIdentidade(correlacaoId, produto, vinculosProdutoIds),
                            cabecalhosBasicos()
                    ),
                    RespostaIdentidade.class
            );
            RespostaIdentidade body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                LOGGER.warn(
                        "exclusao_cadastro_produto_avatar_pendencia_http_invalida correlacaoId={} status={} bodyPresente={}",
                        correlacaoId,
                        response.getStatusCode(),
                        body != null
                );
                return pendenciaIndisponivel("Identidade nao materializou pendencias de remocao de avatar.");
            }
            LOGGER.info(
                    "exclusao_cadastro_produto_avatar_pendencia_http_concluida correlacaoId={} status={} pendencias={} avatarIds={} storageKeys={}",
                    correlacaoId,
                    response.getStatusCode(),
                    body.pendenciasMaterializadas(),
                    body.avatarIds() == null ? 0 : body.avatarIds().size(),
                    body.storageKeys() == null ? 0 : body.storageKeys().size()
            );
            return new Resultado(
                    List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                            SISTEMA_IDENTIDADE,
                            "MATERIALIZAR_PENDENCIA",
                            "identidade.pendencias_remocao_avatar_usuario",
                            body.pendenciasMaterializadas()
                    )),
                    List.of()
            );
        } catch (RestClientException ex) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_avatar_pendencia_http_falhou correlacaoId={} produto={} erro={}",
                    correlacaoId,
                    produto,
                    ex.getClass().getSimpleName(),
                    ex
            );
            return pendenciaIndisponivel("Falha ao materializar pendencias de remocao de avatar na identidade.");
        }
    }

    private Resultado pendenciaIndisponivel(final String detalhe) {
        return new Resultado(
                List.of(),
                List.of(new BloqueioExclusaoCadastroProdutoApiResposta(
                        SISTEMA_IDENTIDADE,
                        "avatar_pendencia_indisponivel",
                        detalhe
                ))
        );
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelIdentidadeKeycloak.obterTokenBearer());
        return headers;
    }

    private static String normalizarUrlBase(final String urlBase) {
        if (urlBase == null || urlBase.isBlank()) {
            throw new IllegalStateException("integracao.identidade.url-base e obrigatorio");
        }
        String valor = urlBase.trim();
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    private record RequisicaoIdentidade(
            String correlacaoId,
            String produto,
            List<String> usuarioClienteIds
    ) {
    }

    private record RespostaIdentidade(
            String correlacaoId,
            String produto,
            int pendenciasMaterializadas,
            List<String> avatarIds,
            List<String> storageKeys
    ) {
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
