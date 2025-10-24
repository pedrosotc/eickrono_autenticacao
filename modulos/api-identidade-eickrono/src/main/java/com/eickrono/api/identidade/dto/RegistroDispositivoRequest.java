package com.eickrono.api.identidade.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload recebido ao iniciar o registro de um dispositivo.
 */
public class RegistroDispositivoRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 5, max = 32)
    private String telefone;

    @NotBlank
    @Size(min = 10, max = 255)
    private String fingerprint;

    @NotBlank
    @Size(min = 2, max = 64)
    private String plataforma;

    @NotBlank
    @Size(min = 1, max = 32)
    private String versaoAplicativo;

    @Size(max = 2048)
    private String chavePublica;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getPlataforma() {
        return plataforma;
    }

    public void setPlataforma(String plataforma) {
        this.plataforma = plataforma;
    }

    public String getVersaoAplicativo() {
        return versaoAplicativo;
    }

    public void setVersaoAplicativo(String versaoAplicativo) {
        this.versaoAplicativo = versaoAplicativo;
    }

    public String getChavePublica() {
        return chavePublica;
    }

    public void setChavePublica(String chavePublica) {
        this.chavePublica = chavePublica;
    }
}
