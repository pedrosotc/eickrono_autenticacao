package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.ExclusaoCadastroProdutoDryRunService;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiResposta;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interna/usuarios/exclusoes")
public class ExclusaoCadastroProdutoController {

    private final ExclusaoCadastroProdutoDryRunService dryRunService;

    public ExclusaoCadastroProdutoController(final ExclusaoCadastroProdutoDryRunService dryRunService) {
        this.dryRunService = Objects.requireNonNull(dryRunService, "dryRunService e obrigatorio");
    }

    @PostMapping
    public ResponseEntity<ExclusaoCadastroProdutoApiResposta> simular(
            @Valid @RequestBody final ExclusaoCadastroProdutoApiRequest requisicao) {
        return ResponseEntity.ok(dryRunService.simular(requisicao));
    }
}
