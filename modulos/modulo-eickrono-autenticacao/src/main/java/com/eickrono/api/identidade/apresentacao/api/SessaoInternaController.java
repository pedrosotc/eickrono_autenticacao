package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna;
import com.eickrono.api.identidade.apresentacao.dto.sessao.CriarSessaoInternaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.sessao.SessaoInternaApiResposta;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identidade/sessoes/interna")
public class SessaoInternaController {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";

    private final AutenticacaoSessaoInternaServico servico;
    private final ValidadorChamadaInterna validadorChamadaInterna;

    public SessaoInternaController(final AutenticacaoSessaoInternaServico servico,
                                   final IntegracaoInternaProperties integracaoInternaProperties,
                                   final ValidadorChamadaInterna validadorChamadaInterna) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
        Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório");
        this.validadorChamadaInterna = Objects.requireNonNull(validadorChamadaInterna, "validadorChamadaInterna é obrigatório");
    }

    @PostMapping
    public SessaoInternaApiResposta abrirSessao(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                @AuthenticationPrincipal final Jwt jwt,
                                                @Valid @RequestBody final CriarSessaoInternaApiRequest request) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "SessaoInternaController");
        SessaoInternaAutenticada sessao = servico.autenticar(request.login(), request.senha());
        return SessaoInternaApiResposta.de(sessao);
    }
}
