package com.eickrono.api.identidade.apresentacao.dto.fluxo;

public record StatusCadastroPublicoApiResposta(
        String cadastroId,
        String emailPrincipal,
        String telefonePrincipal,
        boolean emailConfirmado,
        boolean telefoneConfirmado,
        boolean telefoneObrigatorio,
        boolean liberadoParaLogin,
        String proximoPasso
) {
}
