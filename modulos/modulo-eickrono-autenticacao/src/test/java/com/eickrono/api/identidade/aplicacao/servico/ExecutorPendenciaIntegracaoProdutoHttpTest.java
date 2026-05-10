package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.EntregaIntegracaoProdutoException;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import com.eickrono.api.identidade.infraestrutura.integracao.ClienteTokenBackchannelPerfilKeycloak;
import com.eickrono.api.identidade.infraestrutura.integracao.ExecutorPendenciaIntegracaoProdutoHttp;
import java.io.IOException;
import java.util.UUID;
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
class ExecutorPendenciaIntegracaoProdutoHttpTest {

    @Mock
    private ConfiguradorRestTemplateBackchannelMtls configuradorMtls;

    @Mock
    private ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    private MockWebServer server;
    private ExecutorPendenciaIntegracaoProdutoHttp executor;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RestTemplateBuilder builder = new RestTemplateBuilder();
        when(configuradorMtls.configurar(any(RestTemplateBuilder.class), any(String.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, RestTemplateBuilder.class));
        when(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer()).thenReturn("token-interno");

        PerfilDominioBackchannelProperties perfil = new PerfilDominioBackchannelProperties();
        perfil.setUrlBase(server.url("/").toString());
        IntegracaoInternaProperties interna = new IntegracaoInternaProperties();
        interna.setSegredo("segredo-interno");

        executor = new ExecutorPendenciaIntegracaoProdutoHttp(
                builder,
                configuradorMtls,
                perfil,
                interna,
                clienteTokenBackchannelPerfilKeycloak
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("deve entregar com sucesso para o backend do produto")
    void deveEntregarComSucessoParaBackendProduto() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        assertThatCode(() -> executor.entregar(pendencia("POST"), 3000)).doesNotThrowAnyException();

        RecordedRequest request = server.takeRequest();
        org.assertj.core.api.Assertions.assertThat(request.getPath()).isEqualTo("/api/interna/perfis-sistema/provisionamentos");
        org.assertj.core.api.Assertions.assertThat(request.getMethod()).isEqualTo("POST");
        org.assertj.core.api.Assertions.assertThat(request.getHeader("X-Eickrono-Internal-Secret")).isEqualTo("segredo-interno");
        org.assertj.core.api.Assertions.assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-interno");
        org.assertj.core.api.Assertions.assertThat(request.getBody().readUtf8()).isEqualTo("{\"exemplo\":true}");
    }

    @Test
    @DisplayName("deve reagendar quando o backend do produto retornar erro 5xx")
    void deveReagendarQuandoBackendRetornar5xx() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> executor.entregar(pendencia("POST"), 3000))
                .isInstanceOf(EntregaIntegracaoProdutoException.class)
                .hasMessageContaining("5xx")
                .extracting("codigoErro", "passivelNovaTentativa")
                .containsExactly("HTTP_5XX", true);
    }

    @Test
    @DisplayName("deve escalar quando o backend do produto retornar erro 4xx")
    void deveEscalarQuandoBackendRetornar4xx() {
        server.enqueue(new MockResponse().setResponseCode(409));

        assertThatThrownBy(() -> executor.entregar(pendencia("POST"), 3000))
                .isInstanceOf(EntregaIntegracaoProdutoException.class)
                .hasMessageContaining("4xx")
                .extracting("codigoErro", "passivelNovaTentativa")
                .containsExactly("HTTP_4XX", false);
    }

    private PendenciaIntegracaoProduto pendencia(final String metodoHttp) {
        return new PendenciaIntegracaoProduto(
                UUID.randomUUID(),
                1L,
                "CRIAR_PERFIL_SISTEMA",
                "/api/interna/perfis-sistema/provisionamentos",
                metodoHttp,
                "{\"exemplo\":true}",
                "idempotency-key",
                "v1",
                UUID.randomUUID(),
                10L,
                null,
                "joao123",
                "PENDENTE_ENVIO",
                0
        );
    }
}
