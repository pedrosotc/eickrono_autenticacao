package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.modelo.DesafioAtestacaoGerado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.CriarDesafioAtestacaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.DesafioAtestacaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ValidacaoAtestacaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ValidarAtestacaoApiRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publica/atestacoes")
public class AtestacaoAppPublicaController {

    private final AtestacaoAppServico servico;

    public AtestacaoAppPublicaController(final AtestacaoAppServico servico) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
    }

    @PostMapping("/desafios")
    @ResponseStatus(HttpStatus.CREATED)
    public DesafioAtestacaoApiResposta criarDesafio(@Valid @RequestBody final CriarDesafioAtestacaoApiRequest request,
                                                    final HttpServletRequest servletRequest) {
        DesafioAtestacaoGerado desafio = servico.gerarDesafio(
                request.operacao(),
                request.plataforma(),
                request.usuarioSub(),
                request.pessoaIdPerfil(),
                request.cadastroId(),
                request.registroDispositivoId(),
                extrairEnderecoIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
        return DesafioAtestacaoApiResposta.de(desafio);
    }

    @PostMapping("/validacoes")
    public ValidacaoAtestacaoApiResposta validarAtestacao(@Valid @RequestBody final ValidarAtestacaoApiRequest request) {
        ValidacaoAtestacaoAppConcluida validacao = servico.validarComprovante(request.paraEntradaAplicacao());
        return ValidacaoAtestacaoApiResposta.de(validacao);
    }

    private static String extrairEnderecoIp(final HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int indiceVirgula = forwardedFor.indexOf(',');
            return indiceVirgula >= 0 ? forwardedFor.substring(0, indiceVirgula).trim() : forwardedFor.trim();
        }
        return servletRequest.getRemoteAddr();
    }
}
