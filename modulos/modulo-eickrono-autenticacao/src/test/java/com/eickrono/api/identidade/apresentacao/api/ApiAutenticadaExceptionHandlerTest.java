package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.eickrono.api.identidade.aplicacao.excecao.ApiAutenticadaException;
import com.eickrono.api.identidade.apresentacao.dto.ErroApiAutenticadaResposta;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAutenticadaExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private ApiAutenticadaExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiAutenticadaExceptionHandler();
        when(request.getRequestURI()).thenReturn("/identidade/vinculos-sociais/google");
        when(request.getMethod()).thenReturn("DELETE");
    }

    @Test
    void devePreservarCodigoMensagemEDetalhesDaApiAutenticada() {
        ApiAutenticadaException exception = new ApiAutenticadaException(
                HttpStatus.BAD_REQUEST,
                "senha_confirmacao_obrigatoria",
                "Informe a senha atual para confirmar a desvinculação.",
                Map.of(
                        "provedor", "google",
                        "exigeReautenticacao", true
                )
        );

        ResponseEntity<ErroApiAutenticadaResposta> resposta = handler.tratarApiAutenticada(exception, request);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("senha_confirmacao_obrigatoria");
        assertThat(resposta.getBody().mensagem()).isEqualTo("Informe a senha atual para confirmar a desvinculação.");
        assertThat(resposta.getBody().detalhes()).containsEntry("provedor", "google");
        assertThat(resposta.getBody().detalhes()).containsEntry("exigeReautenticacao", true);
    }

    @Test
    void deveMapearResponseStatusParaErroAutenticadoGenerico() {
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "A senha informada não confere com a conta atual."
        );

        ResponseEntity<ErroApiAutenticadaResposta> resposta = handler.tratarResponseStatus(exception, request);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("api_autenticada_erro");
        assertThat(resposta.getBody().mensagem()).isEqualTo("A senha informada não confere com a conta atual.");
        assertThat(resposta.getBody().detalhes()).isNull();
    }
}
