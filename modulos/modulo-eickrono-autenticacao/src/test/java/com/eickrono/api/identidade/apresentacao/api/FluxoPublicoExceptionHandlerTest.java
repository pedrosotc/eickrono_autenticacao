package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ErroFluxoPublicoApiResposta;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FluxoPublicoExceptionHandlerTest {

    private final FluxoPublicoExceptionHandler handler = new FluxoPublicoExceptionHandler();

    @Test
    void devePreservarCodigoMensagemEDetalhesDoFluxoPublico() {
        FluxoPublicoException exception = new FluxoPublicoException(
                HttpStatus.CONFLICT,
                "social_sem_conta_local",
                "A rede social autenticou, mas ainda nao existe conta local pronta.",
                Map.of(
                        "acaoSugerida", "ENTRAR_E_VINCULAR",
                        "contextoSocialPendenteId", "91919191-9191-9191-9191-919191919191"
                )
        );

        var resposta = handler.tratarFluxoPublico(exception);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErroFluxoPublicoApiResposta corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.codigo()).isEqualTo("social_sem_conta_local");
        assertThat(corpo.mensagem()).isEqualTo("A rede social autenticou, mas ainda nao existe conta local pronta.");
        assertThat(corpo.detalhes()).containsEntry("acaoSugerida", "ENTRAR_E_VINCULAR");
        assertThat(corpo.detalhes()).containsEntry(
                "contextoSocialPendenteId",
                "91919191-9191-9191-9191-919191919191"
        );
    }

    @Test
    void deveMapearResponseStatusParaErroPublicoGenerico() {
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.LOCKED,
                "Este dispositivo nao esta liberado para uso com a conta."
        );

        var resposta = handler.tratarResponseStatus(exception);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        ErroFluxoPublicoApiResposta corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.codigo()).isEqualTo("fluxo_publico_erro");
        assertThat(corpo.mensagem()).isEqualTo("Este dispositivo nao esta liberado para uso com a conta.");
        assertThat(corpo.detalhes()).isNull();
    }
}
