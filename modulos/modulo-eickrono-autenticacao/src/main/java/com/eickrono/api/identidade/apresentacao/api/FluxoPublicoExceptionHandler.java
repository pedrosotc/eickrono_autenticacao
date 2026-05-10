package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ErroFluxoPublicoApiResposta;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {FluxoPublicoController.class, RegistroDispositivoController.class})
public class FluxoPublicoExceptionHandler {

    @ExceptionHandler(FluxoPublicoException.class)
    public ResponseEntity<ErroFluxoPublicoApiResposta> tratarFluxoPublico(final FluxoPublicoException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErroFluxoPublicoApiResposta(
                        exception.getCodigo(),
                        exception.getMessage(),
                        exception.getDetalhes()
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroFluxoPublicoApiResposta> tratarResponseStatus(final ResponseStatusException exception) {
        String mensagem = Objects.requireNonNullElse(exception.getReason(), "Não foi possível concluir a solicitação.");
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErroFluxoPublicoApiResposta("fluxo_publico_erro", mensagem, null));
    }
}
