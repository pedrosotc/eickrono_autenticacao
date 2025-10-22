package com.eickrono.api.identidade.api;

import com.eickrono.api.identidade.dto.PerfilDto;
import com.eickrono.api.identidade.servico.AuditoriaService;
import com.eickrono.api.identidade.servico.PerfilService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de leitura do perfil do usuário autenticado.
 */
@RestController
@RequestMapping("/identidade")
public class PerfilController {

    private final PerfilService perfilService;
    private final AuditoriaService auditoriaService;

    public PerfilController(PerfilService perfilService, AuditoriaService auditoriaService) {
        this.perfilService = perfilService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/perfil")
    public ResponseEntity<PerfilDto> obterPerfil(@AuthenticationPrincipal Jwt jwt) {
        return perfilService.buscarPorSub(jwt.getSubject())
                .map(perfil -> {
                    auditoriaService.registrarEvento("PERFIL_CONSULTADO", jwt.getSubject(), "Perfil consultado");
                    return ResponseEntity.ok(perfil);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
