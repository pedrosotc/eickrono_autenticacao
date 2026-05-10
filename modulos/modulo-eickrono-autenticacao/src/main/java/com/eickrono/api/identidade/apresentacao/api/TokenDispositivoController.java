package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.ValidadorChamadaInterna;
import com.eickrono.api.identidade.apresentacao.dto.ValidacaoTokenDispositivoResponse;
import com.eickrono.api.identidade.aplicacao.servico.ResultadoValidacaoTokenDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.TokenDispositivoService;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint dedicado para validacao explicita do token de dispositivo.
 */
@RestController
@RequestMapping({"/api/conta/dispositivos", "/identidade/dispositivos"})
public class TokenDispositivoController {

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final String HEADER_USUARIO_SUB = "X-Usuario-Sub";
    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";

    private final TokenDispositivoService tokenDispositivoService;
    private final ValidadorChamadaInterna validadorChamadaInterna;

    public TokenDispositivoController(TokenDispositivoService tokenDispositivoService,
                                      IntegracaoInternaProperties integracaoInternaProperties,
                                      ValidadorChamadaInterna validadorChamadaInterna) {
        this.tokenDispositivoService = tokenDispositivoService;
        Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties e obrigatorio");
        this.validadorChamadaInterna = Objects.requireNonNull(validadorChamadaInterna, "validadorChamadaInterna e obrigatorio");
    }

    @GetMapping("/token/validacao")
    public ResponseEntity<ValidacaoTokenDispositivoResponse> validarToken(@AuthenticationPrincipal Jwt jwt,
                                                                          @RequestHeader(HEADER_DEVICE_TOKEN) String tokenDispositivo) {
        ResultadoValidacaoTokenDispositivo resultado =
                tokenDispositivoService.validarToken(jwt.getSubject(), tokenDispositivo);
        return ResponseEntity.ok(new ValidacaoTokenDispositivoResponse(
                resultado.valido(),
                resultado.codigo(),
                resultado.mensagem(),
                resultado.expiraEmOpt().orElse(null)));
    }

    @GetMapping("/token/validacao/interna")
    public ResponseEntity<ValidacaoTokenDispositivoResponse> validarTokenInternamente(
            @AuthenticationPrincipal final Jwt jwt,
            @RequestHeader(HEADER_SEGREDO_INTERNO) String segredoInterno,
            @RequestHeader(HEADER_DEVICE_TOKEN) String tokenDispositivo,
            @RequestHeader(value = HEADER_USUARIO_SUB, required = false) String usuarioSub) {
        validadorChamadaInterna.validar(segredoInterno, jwt, "TokenDispositivoController");
        ResultadoValidacaoTokenDispositivo resultado = StringUtils.hasText(usuarioSub)
                ? tokenDispositivoService.validarToken(usuarioSub, tokenDispositivo)
                : tokenDispositivoService.validarTokenSemUsuario(tokenDispositivo);
        return ResponseEntity.ok(new ValidacaoTokenDispositivoResponse(
                resultado.valido(),
                resultado.codigo(),
                resultado.mensagem(),
                resultado.expiraEmOpt().orElse(null)));
    }
}
