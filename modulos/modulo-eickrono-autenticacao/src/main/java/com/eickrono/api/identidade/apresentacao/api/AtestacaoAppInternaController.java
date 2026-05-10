package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.modelo.DesafioAtestacaoGerado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.CriarDesafioAtestacaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.DesafioAtestacaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ValidacaoAtestacaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ValidarAtestacaoApiRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identidade/atestacoes/interna")
public class AtestacaoAppInternaController {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String HEADER_CLIENT_IP = "X-Eickrono-Client-Ip";
    private static final String HEADER_CLIENT_USER_AGENT = "X-Eickrono-Client-User-Agent";

    private final AtestacaoAppServico servico;
    private final ValidadorChamadaInterna validadorChamadaInterna;

    public AtestacaoAppInternaController(final AtestacaoAppServico servico,
                                         final IntegracaoInternaProperties integracaoInternaProperties,
                                         final ValidadorChamadaInterna validadorChamadaInterna) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
        Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório");
        this.validadorChamadaInterna = Objects.requireNonNull(validadorChamadaInterna, "validadorChamadaInterna é obrigatório");
    }

    @PostMapping("/desafios")
    @ResponseStatus(HttpStatus.CREATED)
    public DesafioAtestacaoApiResposta criarDesafio(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                    @AuthenticationPrincipal final Jwt jwt,
                                                    @RequestHeader(value = HEADER_CLIENT_IP, required = false) final String ipOriginal,
                                                    @RequestHeader(value = HEADER_CLIENT_USER_AGENT, required = false) final String userAgentOriginal,
                                                    @Valid @RequestBody final CriarDesafioAtestacaoApiRequest request) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "AtestacaoAppInternaController");
        DesafioAtestacaoGerado desafio = servico.gerarDesafio(
                request.operacao(),
                request.plataforma(),
                request.usuarioSub(),
                request.pessoaIdPerfil(),
                request.cadastroId(),
                request.registroDispositivoId(),
                ipOriginal,
                userAgentOriginal
        );
        return DesafioAtestacaoApiResposta.de(desafio);
    }

    @PostMapping("/validacoes")
    public ValidacaoAtestacaoApiResposta validarAtestacao(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                          @AuthenticationPrincipal final Jwt jwt,
                                                          @Valid @RequestBody final ValidarAtestacaoApiRequest request) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "AtestacaoAppInternaController");
        ValidacaoAtestacaoAppConcluida validacao = servico.validarComprovante(request.paraEntradaAplicacao());
        return ValidacaoAtestacaoApiResposta.de(validacao);
    }
}
