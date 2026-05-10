package com.eickrono.api.identidade.infraestrutura.atestacao;

import java.time.Duration;

public class GooglePlayIntegrityProperties {

    private boolean habilitado;
    private String packageName;
    private String serviceAccountJsonArquivo;
    private Duration toleranciaTempoToken = Duration.ofMinutes(2);
    private boolean exigirPlayRecognized = true;
    private boolean exigirDeviceIntegrity = true;
    private boolean exigirLicenciado = true;

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(final boolean habilitado) {
        this.habilitado = habilitado;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(final String packageName) {
        this.packageName = packageName;
    }

    public String getServiceAccountJsonArquivo() {
        return serviceAccountJsonArquivo;
    }

    public void setServiceAccountJsonArquivo(final String serviceAccountJsonArquivo) {
        this.serviceAccountJsonArquivo = serviceAccountJsonArquivo;
    }

    public Duration getToleranciaTempoToken() {
        return toleranciaTempoToken;
    }

    public void setToleranciaTempoToken(final Duration toleranciaTempoToken) {
        this.toleranciaTempoToken = toleranciaTempoToken;
    }

    public boolean isExigirPlayRecognized() {
        return exigirPlayRecognized;
    }

    public void setExigirPlayRecognized(final boolean exigirPlayRecognized) {
        this.exigirPlayRecognized = exigirPlayRecognized;
    }

    public boolean isExigirDeviceIntegrity() {
        return exigirDeviceIntegrity;
    }

    public void setExigirDeviceIntegrity(final boolean exigirDeviceIntegrity) {
        this.exigirDeviceIntegrity = exigirDeviceIntegrity;
    }

    public boolean isExigirLicenciado() {
        return exigirLicenciado;
    }

    public void setExigirLicenciado(final boolean exigirLicenciado) {
        this.exigirLicenciado = exigirLicenciado;
    }
}
