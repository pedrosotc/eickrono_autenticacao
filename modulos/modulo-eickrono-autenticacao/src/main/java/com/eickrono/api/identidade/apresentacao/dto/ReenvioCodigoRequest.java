package com.eickrono.api.identidade.apresentacao.dto;

/**
 * Payload opcional para solicitar reenvio de códigos.
 */
public class ReenvioCodigoRequest {

    private Boolean reenviarSms;
    private Boolean reenviarEmail;

    public Boolean getReenviarSms() {
        return reenviarSms;
    }

    public void setReenviarSms(Boolean reenviarSms) {
        this.reenviarSms = reenviarSms;
    }

    public Boolean getReenviarEmail() {
        return reenviarEmail;
    }

    public void setReenviarEmail(Boolean reenviarEmail) {
        this.reenviarEmail = reenviarEmail;
    }

    public boolean deveReenviarSms() {
        return reenviarSms == null || Boolean.TRUE.equals(reenviarSms);
    }

    public boolean deveReenviarEmail() {
        return reenviarEmail == null || Boolean.TRUE.equals(reenviarEmail);
    }
}
