package com.eickrono.api.identidade.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload para revogar o token do dispositivo atual.
 */
public class RevogarTokenRequest {

    @Size(max = 128)
    private String motivo;

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
