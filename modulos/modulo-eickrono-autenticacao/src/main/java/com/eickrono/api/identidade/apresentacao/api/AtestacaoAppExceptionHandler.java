package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.excecao.AtestacaoAppInvalidaException;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ErroAtestacaoApiResposta;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AtestacaoAppExceptionHandler {

    @ExceptionHandler(AtestacaoAppInvalidaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErroAtestacaoApiResposta tratarAtestacaoInvalida(final AtestacaoAppInvalidaException exception) {
        return new ErroAtestacaoApiResposta(exception.getCodigo(), exception.getMessage());
    }
}
