package com.eickrono.api.identidade.apresentacao.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints de leitura do perfil do usuário autenticado.
 */
@RestController
@RequestMapping("/identidade")
public class PerfilController {

    @GetMapping("/perfil")
    public void obterPerfil() {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Endpoint desativado neste serviço. Consulte o perfil no servidor de domínio.");
    }
}
