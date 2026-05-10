package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.VinculoOrganizacionalService;
import com.eickrono.api.identidade.apresentacao.dto.VinculosOrganizacionaisDto;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints para leitura dos vínculos organizacionais do usuário autenticado.
 */
@RestController
@RequestMapping({"/identidade/vinculos-organizacionais", "/api/conta/vinculos-organizacionais"})
public class VinculosOrganizacionaisController {

    private final VinculoOrganizacionalService vinculoOrganizacionalService;

    public VinculosOrganizacionaisController(final VinculoOrganizacionalService vinculoOrganizacionalService) {
        this.vinculoOrganizacionalService = Objects.requireNonNull(
                vinculoOrganizacionalService,
                "vinculoOrganizacionalService é obrigatório"
        );
    }

    @GetMapping
    public ResponseEntity<VinculosOrganizacionaisDto> listar(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoOrganizacionalService.listar(
                Objects.requireNonNull(jwt, "jwt é obrigatório")
        ));
    }
}
