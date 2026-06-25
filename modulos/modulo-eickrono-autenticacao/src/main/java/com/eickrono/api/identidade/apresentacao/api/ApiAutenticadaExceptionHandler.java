package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.excecao.ApiAutenticadaException;
import com.eickrono.api.identidade.apresentacao.dto.ErroApiAutenticadaResposta;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {
        AvatarPreferidoController.class,
        RegistroDispositivoController.class,
        VinculosSociaisController.class
})
public class ApiAutenticadaExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiAutenticadaExceptionHandler.class);

    @ExceptionHandler(ApiAutenticadaException.class)
    public ResponseEntity<ErroApiAutenticadaResposta> tratarApiAutenticada(final ApiAutenticadaException exception,
                                                                           final HttpServletRequest request) {
        LOGGER.warn(
                "api_autenticada_exception path={} method={} codigo={} status={} detalhes={}",
                request.getRequestURI(),
                request.getMethod(),
                exception.getCodigo(),
                exception.getStatus().value(),
                exception.getDetalhes()
        );
        return ResponseEntity.status(exception.getStatus())
                .body(new ErroApiAutenticadaResposta(
                        exception.getCodigo(),
                        exception.getMessage(),
                        exception.getDetalhes()
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroApiAutenticadaResposta> tratarResponseStatus(final ResponseStatusException exception,
                                                                           final HttpServletRequest request) {
        String mensagem = Objects.requireNonNullElse(
                exception.getReason(),
                "Não foi possível concluir a solicitação autenticada."
        );
        LOGGER.warn(
                "api_autenticada_response_status path={} method={} status={} motivo={}",
                request.getRequestURI(),
                request.getMethod(),
                exception.getStatusCode().value(),
                mensagem
        );
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErroApiAutenticadaResposta("api_autenticada_erro", mensagem, null));
    }
}
