package com.eickrono.api.identidade.api;

import com.eickrono.api.identidade.dto.CriarVinculoSocialRequisicao;
import com.eickrono.api.identidade.dto.VinculoSocialDto;
import com.eickrono.api.identidade.servico.VinculoSocialService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints para gerenciar vínculos sociais.
 */
@RestController
@RequestMapping("/identidade/vinculos-sociais")
public class VinculosSociaisController {

    private final VinculoSocialService vinculoSocialService;

    public VinculosSociaisController(VinculoSocialService vinculoSocialService) {
        this.vinculoSocialService = vinculoSocialService;
    }

    @GetMapping
    public List<VinculoSocialDto> listar(@AuthenticationPrincipal Jwt jwt) {
        return vinculoSocialService.listar(jwt.getSubject());
    }

    @PostMapping
    public ResponseEntity<VinculoSocialDto> criar(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CriarVinculoSocialRequisicao requisicao) {
        VinculoSocialDto criado = vinculoSocialService.criar(jwt.getSubject(), requisicao);
        return ResponseEntity.ok(criado);
    }
}
