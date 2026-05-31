package com.eickrono.api.identidade.infraestrutura.integracao;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.eickrono.api.identidade.aplicacao.modelo.AvatarCadastroConfirmado;
import com.eickrono.api.identidade.aplicacao.servico.UploadAvatarCadastroServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IdentidadeBackchannelProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UploadAvatarCadastroIdentidadeHttp implements UploadAvatarCadastroServico {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_UPLOAD_AVATAR = "/identidade/avatares/interna/uploads";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadAvatarCadastroIdentidadeHttp.class);

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelIdentidadeKeycloak clienteTokenBackchannelIdentidadeKeycloak;

    public UploadAvatarCadastroIdentidadeHttp(
            final RestTemplateBuilder restTemplateBuilder,
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
    public AvatarCadastroConfirmado materializar(final AvatarCadastroConfirmado avatar) {
        if (avatar == null || StringUtils.hasText(avatar.urlAvatar()) || !StringUtils.hasText(avatar.conteudoBase64())) {
            return avatar;
        }
        LOGGER.info(
                "qa_avatar_upload_identidade_inicio origem={} contentType={} tamanhoBytesDeclarado={} nomeArquivoPresente={} preferido={}",
                avatar.origem(),
                avatar.contentType(),
                avatar.tamanhoBytes(),
                StringUtils.hasText(avatar.nomeArquivo()),
                avatar.preferido()
        );
        ResponseEntity<UploadAvatarCadastroInternoResponse> response = restTemplate.exchange(
                URI.create(urlBase + CAMINHO_UPLOAD_AVATAR),
                HttpMethod.POST,
                new HttpEntity<>(
                        new UploadAvatarCadastroInternoRequest(
                                avatar.origem(),
                                avatar.nomeArquivo(),
                                avatar.contentType(),
                                avatar.tamanhoBytes(),
                                avatar.conteudoBase64()
                        ),
                        cabecalhosBasicos()),
                UploadAvatarCadastroInternoResponse.class
        );
        UploadAvatarCadastroInternoResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || !StringUtils.hasText(body.urlAvatar())) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Nao foi possivel materializar o avatar de cadastro no servico de identidade."
            );
        }
        LOGGER.info(
                "qa_avatar_upload_identidade_fim origem={} status={} urlAvatarPresente={} storageKeyPresente={} hashPresente={} versaoPresente={} tamanhoBytes={}",
                avatar.origem(),
                response.getStatusCode().value(),
                StringUtils.hasText(body.urlAvatar()),
                StringUtils.hasText(body.storageKey()),
                StringUtils.hasText(body.hashConteudo()),
                StringUtils.hasText(body.versao()),
                body.tamanhoBytes()
        );
        return new AvatarCadastroConfirmado(
                avatar.origem(),
                body.urlAvatar(),
                body.storageKey(),
                avatar.nomeArquivo(),
                body.contentType(),
                body.tamanhoBytes(),
                body.hashConteudo(),
                body.versao(),
                null,
                avatar.preferido()
        );
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelIdentidadeKeycloak.obterTokenBearer());
        return headers;
    }

    private record UploadAvatarCadastroInternoRequest(
            String origem,
            String nomeArquivo,
            String contentType,
            Long tamanhoBytes,
            String conteudoBase64
    ) {
    }

    private record UploadAvatarCadastroInternoResponse(
            String urlAvatar,
            String storageKey,
            String contentType,
            Long tamanhoBytes,
            String hashConteudo,
            String versao
    ) {
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
