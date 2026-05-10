package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.Objects;
import java.util.UUID;

public record PerfilSistemaProjetoPorEmailResolvido(
        UUID perfilSistemaId,
        String emailNormalizado,
        String identificadorPublicoSistemaSugerido) {

    public PerfilSistemaProjetoPorEmailResolvido {
        Objects.requireNonNull(perfilSistemaId, "perfilSistemaId é obrigatório");
        Objects.requireNonNull(emailNormalizado, "emailNormalizado é obrigatório");
        Objects.requireNonNull(identificadorPublicoSistemaSugerido, "identificadorPublicoSistemaSugerido é obrigatório");
    }
}
