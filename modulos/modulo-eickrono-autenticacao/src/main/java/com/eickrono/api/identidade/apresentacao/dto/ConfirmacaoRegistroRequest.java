package com.eickrono.api.identidade.apresentacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload para confirmar registro de dispositivo. O código de e-mail é sempre obrigatório
 * e o código de SMS só é exigido quando o registro foi criado com esse canal habilitado.
 */
public class ConfirmacaoRegistroRequest {

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
