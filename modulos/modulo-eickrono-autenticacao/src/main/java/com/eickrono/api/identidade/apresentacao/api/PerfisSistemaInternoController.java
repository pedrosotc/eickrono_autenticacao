package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.apresentacao.dto.cadastro.DisponibilidadePerfilSistemaInternaApiResposta;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identidade/perfis-sistema/interna")
public class PerfisSistemaInternoController {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String HEADER_SISTEMA_SOLICITANTE = "X-Eickrono-Calling-System";

    private final CadastroContaInternaServico servico;
    private final com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna validadorChamadaInterna;

    public PerfisSistemaInternoController(
            final CadastroContaInternaServico servico,
            final com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna validadorChamadaInterna) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
        this.validadorChamadaInterna = Objects.requireNonNull(validadorChamadaInterna, "validadorChamadaInterna é obrigatório");
    }

    @GetMapping("/disponibilidade")
    public DisponibilidadePerfilSistemaInternaApiResposta consultarDisponibilidadePerfilSistema(
            @RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
            @AuthenticationPrincipal final Jwt jwt,
            @RequestHeader(HEADER_SISTEMA_SOLICITANTE) final String sistemaSolicitante,
            @RequestParam(name = "identificadorPublicoSistema") final String identificadorPublicoSistema) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "PerfisSistemaInternoController");
        boolean disponivel = servico.identificadorPublicoSistemaDisponivel(
                identificadorPublicoSistema,
                sistemaSolicitante
        );
        return new DisponibilidadePerfilSistemaInternaApiResposta(
                identificadorPublicoSistema == null ? "" : identificadorPublicoSistema.trim().toLowerCase(Locale.ROOT),
                disponivel
        );
    }
}
