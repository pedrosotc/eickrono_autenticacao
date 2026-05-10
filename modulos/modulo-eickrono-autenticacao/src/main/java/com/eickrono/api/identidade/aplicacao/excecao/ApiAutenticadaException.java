package com.eickrono.api.identidade.aplicacao.excecao;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class ApiAutenticadaException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final Map<String, Object> detalhes;

    public ApiAutenticadaException(final HttpStatus status,
                                   final String codigo,
                                   final String mensagem) {
        this(status, codigo, mensagem, null);
    }

    public ApiAutenticadaException(final HttpStatus status,
                                   final String codigo,
                                   final String mensagem,
                                   final Map<String, Object> detalhes) {
        super(Objects.requireNonNull(mensagem, "mensagem é obrigatória"));
        this.status = Objects.requireNonNull(status, "status é obrigatório");
        this.codigo = Objects.requireNonNull(codigo, "codigo é obrigatório");
        this.detalhes = detalhes == null || detalhes.isEmpty() ? null : Map.copyOf(detalhes);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, Object> getDetalhes() {
        return detalhes;
    }

    public static ApiAutenticadaException conflito(final String codigo,
                                                   final String mensagem,
                                                   final Map<String, Object> detalhes) {
        return new ApiAutenticadaException(HttpStatus.CONFLICT, codigo, mensagem, detalhes);
    }
}
