package com.eickrono.api.contas.api;

import com.eickrono.api.contas.dto.ContaResumoDto;
import com.eickrono.api.contas.servico.ContaService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de consulta de contas.
 */
@RestController
@RequestMapping("/contas")
public class ContasController {

    private final ContaService contaService;

    public ContasController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_contas:ler')")
    public List<ContaResumoDto> listar(@AuthenticationPrincipal Jwt jwt) {
        return contaService.listarPorCliente(jwt.getSubject());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_contas:ler') and hasAuthority('ROLE_cliente')")
    public ResponseEntity<ContaResumoDto> buscarPorId(@PathVariable Long id,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return contaService.buscarPorId(id, jwt.getSubject())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
