package com.eickrono.api.identidade.apresentacao.dto.cadastro;

import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import jakarta.validation.constraints.NotBlank;

public record CriarCadastroInternoApiRequest(
        @NotBlank String nomeCompleto,
        @NotBlank String emailPrincipal,
        String telefonePrincipal,
        CanalValidacaoTelefoneCadastro tipoValidacaoTelefone,
        @NotBlank String senha
) {
}
