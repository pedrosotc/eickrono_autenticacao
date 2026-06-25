package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.VinculoSocialService;
import com.eickrono.api.identidade.apresentacao.dto.AtualizarAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.UploadAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints canônicos da conta autenticada para definir a foto principal.
 */
@RestController
@RequestMapping("/api/conta/avatar-preferido")
public class AvatarPreferidoController {

    private final VinculoSocialService vinculoSocialService;

    public AvatarPreferidoController(final VinculoSocialService vinculoSocialService) {
        this.vinculoSocialService = Objects.requireNonNull(vinculoSocialService, "vinculoSocialService é obrigatório");
    }

    @PutMapping
    public ResponseEntity<VinculosSociaisDto> atualizar(
            @Valid @RequestBody final AtualizarAvatarPreferidoApiRequest requisicao,
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.atualizarAvatarPreferido(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                Objects.requireNonNull(requisicao, "requisicao é obrigatória")));
    }

    @PutMapping("/upload")
    public ResponseEntity<VinculosSociaisDto> upload(
            @Valid @RequestBody final UploadAvatarPreferidoApiRequest requisicao,
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.uploadAvatarPreferido(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                Objects.requireNonNull(requisicao, "requisicao é obrigatória")));
    }
}
