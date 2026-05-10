package com.eickrono.api.identidade.infraestrutura.atestacao;

public class AppleAppAttestProperties {

    private boolean habilitado;
    private String teamIdentifier;
    private String bundleIdentifier;
    private String rootCaArquivo = "classpath:certificados/Apple_App_Attestation_Root_CA.pem";

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(final boolean habilitado) {
        this.habilitado = habilitado;
    }

    public String getTeamIdentifier() {
        return teamIdentifier;
    }

    public void setTeamIdentifier(final String teamIdentifier) {
        this.teamIdentifier = teamIdentifier;
    }

    public String getBundleIdentifier() {
        return bundleIdentifier;
    }

    public void setBundleIdentifier(final String bundleIdentifier) {
        this.bundleIdentifier = bundleIdentifier;
    }

    public String getRootCaArquivo() {
        return rootCaArquivo;
    }

    public void setRootCaArquivo(final String rootCaArquivo) {
        this.rootCaArquivo = rootCaArquivo;
    }
}
