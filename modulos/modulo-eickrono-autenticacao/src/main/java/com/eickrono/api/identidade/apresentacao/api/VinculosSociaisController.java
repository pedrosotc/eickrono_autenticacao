package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.VinculoSocialService;
import com.eickrono.api.identidade.aplicacao.modelo.VinculoSocialConfirmadoCadastro;
import com.eickrono.api.identidade.apresentacao.dto.AtualizarAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VincularRedeSocialApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoSenhaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.UploadAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints para gerenciar vínculos sociais.
 */
@RestController
@RequestMapping({"/identidade/vinculos-sociais", "/api/conta/redes-sociais"})
public class VinculosSociaisController {

    private final VinculoSocialService vinculoSocialService;

    public VinculosSociaisController(final VinculoSocialService vinculoSocialService) {
        this.vinculoSocialService = Objects.requireNonNull(vinculoSocialService, "vinculoSocialService é obrigatório");
    }

    @GetMapping
    public ResponseEntity<VinculosSociaisDto> listar(@AuthenticationPrincipal final Jwt jwt,
                                                     @RequestParam(value = "aplicacaoId", required = false)
                                                     final String aplicacaoId) {
        return ResponseEntity.ok(vinculoSocialService.listar(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                aplicacaoId));
    }

    @PostMapping("/{provedor}")
    public ResponseEntity<VinculosSociaisDto> vincular(@PathVariable("provedor") final String provedor,
                                                       @Valid @RequestBody final VincularRedeSocialApiRequest requisicao,
                                                       @AuthenticationPrincipal final Jwt jwt) {
        VincularRedeSocialApiRequest requisicaoLocal = Objects.requireNonNull(requisicao, "requisicao é obrigatória");
        return ResponseEntity.ok(vinculoSocialService.vincularConfirmado(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                provedor,
                new VinculoSocialConfirmadoCadastro(
                        provedor,
                        requisicaoLocal.identificadorExterno(),
                        requisicaoLocal.nomeUsuarioExterno(),
                        requisicaoLocal.email(),
                        primeiroTexto(requisicaoLocal.nomeCompleto(), requisicaoLocal.nomeExibicaoExterno()),
                        requisicaoLocal.urlAvatarExterno(),
                        Boolean.TRUE.equals(requisicaoLocal.avatarPreferido())
                ),
                requisicaoLocal.aplicacaoId()));
    }

    @DeleteMapping("/{provedor}")
    public ResponseEntity<VinculosSociaisDto> remover(@PathVariable("provedor") final String provedor,
                                                      @RequestParam(value = "aplicacaoId", required = false)
                                                      final String aplicacaoId,
                                                      @RequestBody(required = false) final ConfirmacaoSenhaApiRequest requisicao,
                                                      @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.remover(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                provedor,
                requisicao == null ? null : requisicao.senhaObrigatoria(),
                aplicacaoId));
    }

    @PutMapping("/avatar-preferido")
    public ResponseEntity<VinculosSociaisDto> atualizarAvatarPreferido(
            @Valid @RequestBody final AtualizarAvatarPreferidoApiRequest requisicao,
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.atualizarAvatarPreferido(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                Objects.requireNonNull(requisicao, "requisicao é obrigatória")));
    }

    @PutMapping("/avatar-preferido/upload")
    public ResponseEntity<VinculosSociaisDto> uploadAvatarPreferido(
            @Valid @RequestBody final UploadAvatarPreferidoApiRequest requisicao,
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.uploadAvatarPreferido(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                Objects.requireNonNull(requisicao, "requisicao é obrigatória")));
    }

    private static boolean temTexto(final String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String primeiroTexto(final String primeiro, final String segundo) {
        if (temTexto(primeiro)) {
            return primeiro.trim();
        }
        if (temTexto(segundo)) {
            return segundo.trim();
        }
        return null;
    }
}
