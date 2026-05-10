package com.eickrono.api.identidade.dominio.modelo;

import java.util.Objects;

/**
 * Plataformas móveis suportadas pelo fluxo nativo de atestação.
 */
public enum PlataformaAtestacaoApp {
    ANDROID(ProvedorAtestacaoApp.GOOGLE_PLAY_INTEGRITY),
    IOS(ProvedorAtestacaoApp.APPLE_APP_ATTEST);

    private final ProvedorAtestacaoApp provedorPadrao;

    PlataformaAtestacaoApp(final ProvedorAtestacaoApp provedorPadrao) {
        this.provedorPadrao = Objects.requireNonNull(provedorPadrao, "provedorPadrao é obrigatório");
    }

    public ProvedorAtestacaoApp getProvedorPadrao() {
        return provedorPadrao;
    }

    public boolean aceita(final TipoComprovanteAtestacaoApp tipoComprovante) {
        return switch (this) {
            case ANDROID -> tipoComprovante == TipoComprovanteAtestacaoApp.TOKEN_INTEGRIDADE;
            case IOS -> tipoComprovante == TipoComprovanteAtestacaoApp.OBJETO_ATESTACAO
                    || tipoComprovante == TipoComprovanteAtestacaoApp.OBJETO_ASSERCAO;
        };
    }
}
