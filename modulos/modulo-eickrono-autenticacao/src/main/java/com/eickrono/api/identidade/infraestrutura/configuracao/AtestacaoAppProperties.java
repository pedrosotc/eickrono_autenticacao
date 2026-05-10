package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.eickrono.api.identidade.infraestrutura.atestacao.AppleAppAttestProperties;
import com.eickrono.api.identidade.infraestrutura.atestacao.GooglePlayIntegrityProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do fluxo de desafio e atestacao nativa do app.
 */
@ConfigurationProperties(prefix = "identidade.atestacao.app")
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Spring Boot faz o bind de propriedades aninhadas mutaveis nesses beans de configuracao."
)
public class AtestacaoAppProperties {

    private Duration duracaoDesafio = Duration.ofMinutes(5);
    private String numeroProjetoNuvemAndroid;
    private boolean permitirValidacaoLocalSemProvedorOficial = true;
    private GooglePlayIntegrityProperties google = new GooglePlayIntegrityProperties();
    private AppleAppAttestProperties apple = new AppleAppAttestProperties();

    public Duration getDuracaoDesafio() {
        return duracaoDesafio;
    }

    public void setDuracaoDesafio(final Duration duracaoDesafio) {
        this.duracaoDesafio = duracaoDesafio;
    }

    public String getNumeroProjetoNuvemAndroid() {
        return numeroProjetoNuvemAndroid;
    }

    public void setNumeroProjetoNuvemAndroid(final String numeroProjetoNuvemAndroid) {
        this.numeroProjetoNuvemAndroid = numeroProjetoNuvemAndroid;
    }

    public boolean isPermitirValidacaoLocalSemProvedorOficial() {
        return permitirValidacaoLocalSemProvedorOficial;
    }

    public void setPermitirValidacaoLocalSemProvedorOficial(final boolean permitirValidacaoLocalSemProvedorOficial) {
        this.permitirValidacaoLocalSemProvedorOficial = permitirValidacaoLocalSemProvedorOficial;
    }

    public GooglePlayIntegrityProperties getGoogle() {
        return google;
    }

    public void setGoogle(final GooglePlayIntegrityProperties google) {
        this.google = google;
    }

    public AppleAppAttestProperties getApple() {
        return apple;
    }

    public void setApple(final AppleAppAttestProperties apple) {
        this.apple = apple;
    }
}
