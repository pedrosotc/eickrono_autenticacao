package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades para correlação de sinais locais de segurança do app.
 */
@ConfigurationProperties(prefix = "identidade.seguranca.app")
public class SegurancaAplicativoProperties {

    private boolean habilitado = true;
    private boolean modoObservacao = true;
    private int scoreMaximoPermitido = 69;
    private List<String> aplicacoesPermitidas = new ArrayList<>();

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(final boolean habilitado) {
        this.habilitado = habilitado;
    }

    public boolean isModoObservacao() {
        return modoObservacao;
    }

    public void setModoObservacao(final boolean modoObservacao) {
        this.modoObservacao = modoObservacao;
    }

    public int getScoreMaximoPermitido() {
        return scoreMaximoPermitido;
    }

    public void setScoreMaximoPermitido(final int scoreMaximoPermitido) {
        this.scoreMaximoPermitido = scoreMaximoPermitido;
    }

    public List<String> getAplicacoesPermitidas() {
        return aplicacoesPermitidas;
    }

    public void setAplicacoesPermitidas(final List<String> aplicacoesPermitidas) {
        this.aplicacoesPermitidas = aplicacoesPermitidas == null ? new ArrayList<>() : new ArrayList<>(aplicacoesPermitidas);
    }
}
