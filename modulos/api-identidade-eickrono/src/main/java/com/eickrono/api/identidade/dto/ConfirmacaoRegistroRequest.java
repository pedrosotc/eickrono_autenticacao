package com.eickrono.api.identidade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload para confirmar registro de dispositivo com códigos SMS e e-mail.
 */
public class ConfirmacaoRegistroRequest {

    @NotBlank
    @Size(min = 6, max = 6)
    @Pattern(regexp = "^[0-9]{6}$")
    private String codigoSms;

    @NotBlank
    @Size(min = 6, max = 6)
    @Pattern(regexp = "^[0-9]{6}$")
    private String codigoEmail;

    public String getCodigoSms() {
        return codigoSms;
    }

    public void setCodigoSms(String codigoSms) {
        this.codigoSms = codigoSms;
    }

    public String getCodigoEmail() {
        return codigoEmail;
    }

    public void setCodigoEmail(String codigoEmail) {
        this.codigoEmail = codigoEmail;
    }
}
