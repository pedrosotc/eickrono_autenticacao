package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import com.eickrono.api.identidade.infraestrutura.integracao.SondagemOperacionalProdutoHttp;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

@ExtendWith(MockitoExtension.class)
class SondagemOperacionalProdutoHttpTest {

    @Mock
    private ConfiguradorRestTemplateBackchannelMtls configuradorMtls;

    private MockWebServer server;
    private SondagemOperacionalProdutoHttp sondagem;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        when(configuradorMtls.configurar(any(RestTemplateBuilder.class), any(String.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, RestTemplateBuilder.class));

        PerfilDominioBackchannelProperties perfil = new PerfilDominioBackchannelProperties();
        perfil.setUrlBase(server.url("/").toString());

        sondagem = new SondagemOperacionalProdutoHttp(
                new RestTemplateBuilder(),
                configuradorMtls,
                perfil
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("deve retornar verdadeiro quando o produto responder 2xx")
    void deveRetornarVerdadeiroQuandoProdutoResponder2xx() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"UP\"}"));

        boolean disponivel = sondagem.produtoDisponivel(3000);

        assertThat(disponivel).isTrue();
    }

    @Test
    @DisplayName("deve retornar falso quando o produto responder 5xx")
    void deveRetornarFalsoQuandoProdutoResponder5xx() {
        server.enqueue(new MockResponse().setResponseCode(503));

        boolean disponivel = sondagem.produtoDisponivel(3000);

        assertThat(disponivel).isFalse();
    }
}
