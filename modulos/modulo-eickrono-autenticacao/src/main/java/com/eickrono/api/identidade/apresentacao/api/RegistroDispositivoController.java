package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.PerfilSistemaProjetoPorEmailResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.LocalizadorPerfilSistemaProjetoPorEmailJdbc;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.ReenvioCodigoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistrarEventosOfflineRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoSessaoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RevogarTokenRequest;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.aplicacao.servico.OfflineDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.ResolvedorProjetoFluxoPublico;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoService;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints responsáveis pelo registro e revogação de dispositivos móveis.
 */
@RestController
@RequestMapping({"/identidade/dispositivos", "/api/conta/dispositivos"})
public class RegistroDispositivoController {
    private static final String STATUS_LIBERADO = "LIBERADO";
    private static final String STATUS_ATIVO = "ATIVO";
    private static final String STATUS_PENDENTE_LIBERACAO_PRODUTO = "PENDENTE_LIBERACAO_PRODUTO";

    private final RegistroDispositivoService registroDispositivoService;
    private final OfflineDispositivoService offlineDispositivoService;
    private final RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;
    private final CadastroContaInternaServico cadastroContaInternaServico;
    private final ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;
    private final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;
    private final LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail;

    public RegistroDispositivoController(RegistroDispositivoService registroDispositivoService,
                                         OfflineDispositivoService offlineDispositivoService,
                                         RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService,
                                         CadastroContaInternaServico cadastroContaInternaServico,
                                         ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak,
                                         ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico,
                                         LocalizadorPerfilSistemaProjetoPorEmailJdbc localizadorPerfilSistemaProjetoPorEmail) {
        this.registroDispositivoService = registroDispositivoService;
        this.offlineDispositivoService = offlineDispositivoService;
        this.registroDispositivoLoginSilenciosoService = registroDispositivoLoginSilenciosoService;
        this.cadastroContaInternaServico = cadastroContaInternaServico;
        this.clienteAdministracaoVinculosSociaisKeycloak = clienteAdministracaoVinculosSociaisKeycloak;
        this.resolvedorProjetoFluxoPublico = resolvedorProjetoFluxoPublico;
        this.localizadorPerfilSistemaProjetoPorEmail = localizadorPerfilSistemaProjetoPorEmail;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroDispositivoResponse> solicitarRegistro(@Valid @RequestBody RegistroDispositivoRequest request,
                                                                         @AuthenticationPrincipal Jwt jwt) {
        RegistroDispositivoResponse resposta = registroDispositivoService.solicitarRegistro(request, Optional.ofNullable(jwt));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resposta);
    }

    @PostMapping("/registro/silencioso")
    public ResponseEntity<RegistroDispositivoSessaoResponse> registrarSessaoSilenciosa(
            @Valid @RequestBody DispositivoSessaoApiRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String usuarioSub = extrairSub(jwt)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado ausente"
                ));
        Optional<ContextoPessoaPerfilSistema> contextoDireto =
                cadastroContaInternaServico.buscarContextoCentralPorSubPublico(usuarioSub);
        if (contextoDireto.isEmpty()) {
            throw montarErroSocialSemContaLocal(jwt, request);
        }
        validarContaLiberadaParaSessaoSocial(contextoDireto.get());
        DispositivoSessaoRegistrado resposta = registroDispositivoLoginSilenciosoService.registrar(
                contextoDireto.get(),
                request
        );
        return ResponseEntity.ok(new RegistroDispositivoSessaoResponse(
                resposta.tokenDispositivo(),
                resposta.tokenDispositivoExpiraEm()
        ));
    }

    @PostMapping("/registro/{id}/confirmacao")
    public ResponseEntity<ConfirmacaoRegistroResponse> confirmarRegistro(@PathVariable("id") UUID id,
                                                                         @Valid @RequestBody ConfirmacaoRegistroRequest request,
                                                                         @AuthenticationPrincipal Jwt jwt) {
        ConfirmacaoRegistroResponse resposta = registroDispositivoService.confirmarRegistro(id, request, Optional.ofNullable(jwt));
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/offline/eventos")
    public ResponseEntity<Void> registrarEventosOffline(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestHeader("X-Device-Token") String tokenDispositivo,
                                                        @RequestBody RegistrarEventosOfflineRequest request) {
        offlineDispositivoService.registrarEventosOffline(
                extrairSub(jwt).orElseThrow(),
                tokenDispositivo,
                request);
        return ResponseEntity.accepted().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/offline/politica")
    public ResponseEntity<PoliticaOfflineDispositivoResponse> obterPoliticaOffline() {
        return ResponseEntity.ok(offlineDispositivoService.obterPolitica());
    }

    @PostMapping("/registro/{id}/reenviar")
    public ResponseEntity<Void> reenviarCodigos(@PathVariable("id") UUID id,
                                                @RequestBody(required = false) ReenvioCodigoRequest request) {
        registroDispositivoService.reenviarCodigos(id, request == null ? new ReenvioCodigoRequest() : request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/revogar")
    public ResponseEntity<Void> revogarToken(@AuthenticationPrincipal Jwt jwt,
                                             @RequestHeader("X-Device-Token") String tokenDispositivo,
                                             @RequestBody(required = false) RevogarTokenRequest request) {
        MotivoRevogacaoToken motivo = Optional.ofNullable(request)
                .map(RevogarTokenRequest::getMotivo)
                .flatMap(this::mapearMotivo)
                .orElse(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
        registroDispositivoService.revogarToken(
                extrairSub(jwt).orElseThrow(),
                tokenDispositivo,
                motivo);
        return ResponseEntity.noContent().build();
    }

    private Optional<String> extrairSub(Jwt jwt) {
        return Optional.ofNullable(jwt).map(Jwt::getSubject);
    }

    private Optional<MotivoRevogacaoToken> mapearMotivo(String valor) {
        if (!StringUtils.hasText(valor)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MotivoRevogacaoToken.valueOf(valor.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void validarContaLiberadaParaSessaoSocial(final ContextoPessoaPerfilSistema contexto) {
        String statusPerfilSistema = Optional.ofNullable(contexto.statusPerfilSistema()).orElse(STATUS_LIBERADO);
        if (STATUS_LIBERADO.equalsIgnoreCase(statusPerfilSistema)
                || STATUS_ATIVO.equalsIgnoreCase(statusPerfilSistema)
                || STATUS_PENDENTE_LIBERACAO_PRODUTO.equalsIgnoreCase(statusPerfilSistema)) {
            return;
        }
        throw new FluxoPublicoException(
                HttpStatus.FORBIDDEN,
                "conta_nao_liberada",
                "A conta ainda não está liberada para utilizar o aplicativo."
        );
    }

    private FluxoPublicoException montarErroSocialSemContaLocal(final Jwt jwt,
                                                                final DispositivoSessaoApiRequest request) {
        Optional<String> emailSocial = extrairEmail(jwt);
        Optional<PerfilSistemaProjetoPorEmailResolvido> contaExistente =
                resolverPerfilSistemaNoProjetoAtual(
                        request == null ? null : request.aplicacaoId(),
                        emailSocial);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("sub", jwt == null ? null : jwt.getSubject());
        emailSocial.ifPresent(email -> detalhes.put("email", email));
        if (contaExistente.isPresent()) {
            detalhes.put("acaoSugerida", "ENTRAR_E_VINCULAR");
            detalhes.put("emailContaExistente", contaExistente.get().emailNormalizado());
            detalhes.put("loginSugerido", contaExistente.get().identificadorPublicoSistemaSugerido());
        }
        if (request != null && StringUtils.hasText(request.aplicacaoId())) {
            try {
                listarIdentidadesFederadasSeguras(jwt == null ? null : jwt.getSubject()).stream()
                        .findFirst()
                        .ifPresent(identidade -> {
                            detalhes.put("provedor", identidade.provedor().getAliasApi());
                            detalhes.put("identificadorExterno", identidade.identificadorExterno());
                            if (StringUtils.hasText(identidade.nomeUsuarioExterno())) {
                                detalhes.put("nomeUsuarioExterno", identidade.nomeUsuarioExterno());
                            }
                            String nomeExibicao = normalizarTexto(
                                    identidade.nomeExibicaoExterno(),
                                    jwt == null ? null : jwt.getClaimAsString("name"));
                            String urlAvatarExterno = normalizarTexto(
                                    identidade.urlAvatarExterno(),
                                    jwt == null ? null : jwt.getClaimAsString("picture"),
                                    jwt == null ? null : jwt.getClaimAsString("avatar_url"),
                                    jwt == null ? null : jwt.getClaimAsString("avatar"));
                            if (StringUtils.hasText(nomeExibicao)) {
                                detalhes.put("nomeExibicaoExterno", nomeExibicao);
                            }
                            if (StringUtils.hasText(urlAvatarExterno)) {
                                detalhes.put("urlAvatarExterno", urlAvatarExterno);
                            }
                        });
            } catch (RuntimeException ignored) {
                // Mantém o contrato mínimo quando a infraestrutura complementar falhar.
            }
        }
        if (contaExistente.isPresent()) {
            return new FluxoPublicoException(
                    HttpStatus.CONFLICT,
                    "social_sem_conta_local",
                    "Ja existe uma conta neste projeto com o mesmo e-mail desta rede social. Deseja entrar e vincular agora?",
                    detalhes
            );
        }
        detalhes.put("acaoSugerida", "ABRIR_CADASTRO");
        return new FluxoPublicoException(
                HttpStatus.CONFLICT,
                "social_sem_conta_local",
                "Esta rede social foi autenticada com sucesso, mas ainda nao esta ligada a uma conta local. Deseja abrir o cadastro com os dados recebidos?",
                detalhes
        );
    }

    private Optional<String> extrairEmail(final Jwt jwt) {
        return Optional.ofNullable(jwt)
                .map(token -> token.getClaimAsString("email"))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .map(valor -> valor.toLowerCase(Locale.ROOT));
    }

    private Optional<PerfilSistemaProjetoPorEmailResolvido> resolverPerfilSistemaNoProjetoAtual(
            final String aplicacaoId,
            final Optional<String> emailSocial) {
        if (!StringUtils.hasText(aplicacaoId) || emailSocial.isEmpty()) {
            return Optional.empty();
        }
        try {
            ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(aplicacaoId);
            return localizadorPerfilSistemaProjetoPorEmail.localizar(
                    projeto.clienteEcossistemaId(),
                    emailSocial.get());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String normalizarTexto(final String... valores) {
        for (String valor : valores) {
            if (StringUtils.hasText(valor)) {
                return valor.trim();
            }
        }
        return null;
    }

    private java.util.List<IdentidadeFederadaKeycloak> listarIdentidadesFederadasSeguras(final String usuarioSub) {
        if (!StringUtils.hasText(usuarioSub)) {
            return java.util.List.of();
        }
        try {
            return clienteAdministracaoVinculosSociaisKeycloak.listarIdentidadesFederadas(usuarioSub);
        } catch (RuntimeException ignored) {
            return java.util.List.of();
        }
    }
}
