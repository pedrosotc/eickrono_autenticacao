package com.eickrono.api.contas.api;

import com.eickrono.api.contas.dto.TransacaoDto;
import com.eickrono.api.contas.servico.TransacaoService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints relacionados às transações financeiras.
 */
@RestController
@RequestMapping("/transacoes")
public class TransacoesController {

    private final TransacaoService transacaoService;

    public TransacoesController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_transacoes:ler') and hasAuthority('ROLE_cliente')")
    public List<TransacaoDto> listar(@RequestParam("contaId") Long contaId,
                                     @AuthenticationPrincipal Jwt jwt) {
        return transacaoService.listarPorConta(contaId, jwt.getSubject());
    }
}
