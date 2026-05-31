package com.eickrono.api.identidade.infraestrutura.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.AvatarCadastroConfirmado;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IdentidadeBackchannelProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

@ExtendWith(MockitoExtension.class)
class UploadAvatarCadastroIdentidadeHttpTest {

    @Mock
    private ConfiguradorRestTemplateBackchannelMtls configuradorMtls;

    @Mock
    private ClienteTokenBackchannelIdentidadeKeycloak clienteTokenBackchannelIdentidadeKeycloak;

    private MockWebServer server;
    private UploadAvatarCadastroIdentidadeHttp uploadAvatar;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        when(configuradorMtls.configurar(any(RestTemplateBuilder.class), any(String.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, RestTemplateBuilder.class));

        IdentidadeBackchannelProperties identidade = new IdentidadeBackchannelProperties();
        identidade.setUrlBase(server.url("/").toString().replaceAll("/$", ""));
        IntegracaoInternaProperties interna = new IntegracaoInternaProperties();
        interna.setSegredo("segredo-interno");

        uploadAvatar = new UploadAvatarCadastroIdentidadeHttp(
                new RestTemplateBuilder(),
                identidade,
                interna,
                configuradorMtls,
                clienteTokenBackchannelIdentidadeKeycloak
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("deve enviar avatar local para o identidade e retornar avatar materializado")
    void deveEnviarAvatarLocalParaIdentidadeERetornarAvatarMaterializado() throws Exception {
        when(clienteTokenBackchannelIdentidadeKeycloak.obterTokenBearer()).thenReturn("token-identidade");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "urlAvatar":"https://cdn.eickrono.test/avatares/thimisu/avatar.png",
                          "storageKey":"avatares/thimisu/hash.png",
                          "contentType":"image/png",
                          "tamanhoBytes":68,
                          "hashConteudo":"hash-conteudo",
                          "versao":"v-avatar-1"
                        }
                        """));

        AvatarCadastroConfirmado materializado = uploadAvatar.materializar(avatarLocal());

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/identidade/avatares/interna/uploads");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("X-Eickrono-Internal-Secret")).isEqualTo("segredo-interno");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-identidade");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        assertThat(request.getBody().readUtf8())
                .contains("\"origem\":\"THIMISU\"")
                .contains("\"nomeArquivo\":\"avatar.png\"")
                .contains("\"contentType\":\"image/png\"")
                .contains("\"tamanhoBytes\":68")
                .contains("\"conteudoBase64\":\"base64-avatar\"");

        assertThat(materializado.origem()).isEqualTo("THIMISU");
        assertThat(materializado.urlAvatar()).isEqualTo("https://cdn.eickrono.test/avatares/thimisu/avatar.png");
        assertThat(materializado.storageKey()).isEqualTo("avatares/thimisu/hash.png");
        assertThat(materializado.nomeArquivo()).isEqualTo("avatar.png");
        assertThat(materializado.contentType()).isEqualTo("image/png");
        assertThat(materializado.tamanhoBytes()).isEqualTo(68L);
        assertThat(materializado.hashConteudo()).isEqualTo("hash-conteudo");
        assertThat(materializado.versao()).isEqualTo("v-avatar-1");
        assertThat(materializado.conteudoBase64()).isNull();
        assertThat(materializado.preferido()).isTrue();
    }

    @Test
    @DisplayName("nao deve enviar upload quando o avatar ja possui URL")
    void naoDeveEnviarUploadQuandoAvatarJaPossuiUrl() {
        AvatarCadastroConfirmado avatar = new AvatarCadastroConfirmado(
                "GOOGLE",
                "https://lh3.googleusercontent.com/avatar",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );

        AvatarCadastroConfirmado materializado = uploadAvatar.materializar(avatar);

        assertThat(materializado).isSameAs(avatar);
        assertThat(server.getRequestCount()).isZero();
        verifyNoInteractions(clienteTokenBackchannelIdentidadeKeycloak);
    }

    @Test
    @DisplayName("nao deve enviar upload quando nao ha conteudo local")
    void naoDeveEnviarUploadQuandoNaoHaConteudoLocal() {
        AvatarCadastroConfirmado avatar = new AvatarCadastroConfirmado(
                "THIMISU",
                null,
                null,
                "avatar.png",
                "image/png",
                68L,
                null,
                null,
                null,
                false
        );

        assertThatCode(() -> uploadAvatar.materializar(avatar)).doesNotThrowAnyException();
        assertThat(server.getRequestCount()).isZero();
        verifyNoInteractions(clienteTokenBackchannelIdentidadeKeycloak);
    }

    private static AvatarCadastroConfirmado avatarLocal() {
        return new AvatarCadastroConfirmado(
                "THIMISU",
                null,
                null,
                "avatar.png",
                "image/png",
                68L,
                null,
                null,
                "base64-avatar",
                true
        );
    }
}
