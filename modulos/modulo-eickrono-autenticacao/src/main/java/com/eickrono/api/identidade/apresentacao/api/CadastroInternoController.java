package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroInternoRealizada;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.apresentacao.dto.cadastro.CadastroInternoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.cadastro.ConfirmacaoEmailCadastroInternoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.cadastro.ConfirmarEmailCadastroInternoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.cadastro.CriarCadastroInternoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DisponibilidadeUsuarioCadastroApiResposta;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identidade/cadastros/interna")
public class CadastroInternoController {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String HEADER_CLIENT_IP = "X-Eickrono-Client-Ip";
    private static final String HEADER_CLIENT_USER_AGENT = "X-Eickrono-Client-User-Agent";
    private static final String HEADER_SISTEMA_SOLICITANTE = "X-Eickrono-Calling-System";

    private final CadastroContaInternaServico servico;
    private final com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna validadorChamadaInterna;

    public CadastroInternoController(final CadastroContaInternaServico servico,
                                     final IntegracaoInternaProperties integracaoInternaProperties,
                                     final com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna validadorChamadaInterna) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
        Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório");
        this.validadorChamadaInterna = Objects.requireNonNull(validadorChamadaInterna, "validadorChamadaInterna é obrigatório");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroInternoApiResposta criarCadastro(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                    @AuthenticationPrincipal final Jwt jwt,
                                                    @RequestHeader(value = HEADER_SISTEMA_SOLICITANTE, required = false)
                                                    final String sistemaSolicitante,
                                                    @RequestHeader(value = HEADER_CLIENT_IP, required = false)
                                                    final String ipSolicitante,
                                                    @RequestHeader(value = HEADER_CLIENT_USER_AGENT, required = false)
                                                    final String userAgentSolicitante,
                                                    @Valid @RequestBody final CriarCadastroInternoApiRequest request) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "CadastroInternoController");
        CadastroInternoRealizado cadastro = servico.cadastrar(
                request.nomeCompleto(),
                request.emailPrincipal(),
                request.telefonePrincipal(),
                request.tipoValidacaoTelefone(),
                request.senha(),
                Objects.requireNonNullElse(sistemaSolicitante, "identidade-servidor"),
                ipSolicitante,
                userAgentSolicitante
        );
        return CadastroInternoApiResposta.de(cadastro);
    }

    @PostMapping("/{cadastroId}/confirmacoes/email")
    public ConfirmacaoEmailCadastroInternoApiResposta confirmarEmail(
            @RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final UUID cadastroId,
            @Valid @RequestBody final ConfirmarEmailCadastroInternoApiRequest request) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "CadastroInternoController");
        ConfirmacaoEmailCadastroInternoRealizada confirmacao = servico.confirmarEmail(cadastroId, request.codigo());
        return ConfirmacaoEmailCadastroInternoApiResposta.de(confirmacao);
    }

    @PostMapping("/{cadastroId}/confirmacoes/email/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarCodigoEmail(
            @RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final UUID cadastroId) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "CadastroInternoController");
        servico.reenviarCodigoEmail(cadastroId);
    }

    @GetMapping("/usuarios/disponibilidade")
    public DisponibilidadeUsuarioCadastroApiResposta consultarDisponibilidadeUsuario(
            @RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
            @AuthenticationPrincipal final Jwt jwt,
            @RequestHeader(HEADER_SISTEMA_SOLICITANTE) final String sistemaSolicitante,
            @RequestParam final String usuario) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "CadastroInternoController");
        boolean disponivel = servico.identificadorPublicoSistemaDisponivel(usuario, sistemaSolicitante);
        return new DisponibilidadeUsuarioCadastroApiResposta(
                usuario == null ? "" : usuario.trim().toLowerCase(java.util.Locale.ROOT),
                disponivel
        );
    }
}
