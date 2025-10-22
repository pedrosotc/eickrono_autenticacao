package com.eickrono.api.identidade.api;

import com.eickrono.api.identidade.servico.ChavesPublicasService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints para expor as chaves públicas consumidas do Keycloak.
 */
@RestController
@RequestMapping("/.well-known")
public class ChavesPublicasController {

    private final ChavesPublicasService chavesPublicasService;

    public ChavesPublicasController(ChavesPublicasService chavesPublicasService) {
        this.chavesPublicasService = chavesPublicasService;
    }

    @GetMapping(value = "/chaves-publicas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> obterChavesPublicas() {
        return ResponseEntity.ok(chavesPublicasService.obterChavesPublicas());
    }
}
