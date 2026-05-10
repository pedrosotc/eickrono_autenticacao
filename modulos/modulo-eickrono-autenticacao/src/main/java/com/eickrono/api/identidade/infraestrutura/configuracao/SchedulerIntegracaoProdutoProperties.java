package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eickrono.integracao-produto.scheduler")
public class SchedulerIntegracaoProdutoProperties {

    private boolean habilitado = true;
    private Duration intervaloCiclo = Duration.ofMinutes(1);
    private int tempoEntreTentativasSegundos = 300;
    private int quantidadeMaximaTentativas = 10;
    private int quantidadeMaximaItensPorCiclo = 50;
    private int timeoutSondagemMillis = 3000;
    private int timeoutEntregaMillis = 10000;
    private int timeoutRecuperacaoProcessamentoSegundos = 900;

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(final boolean habilitado) {
        this.habilitado = habilitado;
    }

    public Duration getIntervaloCiclo() {
        return intervaloCiclo;
    }

    public void setIntervaloCiclo(final Duration intervaloCiclo) {
        this.intervaloCiclo = intervaloCiclo;
    }

    public int getTempoEntreTentativasSegundos() {
        return tempoEntreTentativasSegundos;
    }

    public void setTempoEntreTentativasSegundos(final int tempoEntreTentativasSegundos) {
        this.tempoEntreTentativasSegundos = tempoEntreTentativasSegundos;
    }

    public int getQuantidadeMaximaTentativas() {
        return quantidadeMaximaTentativas;
    }

    public void setQuantidadeMaximaTentativas(final int quantidadeMaximaTentativas) {
        this.quantidadeMaximaTentativas = quantidadeMaximaTentativas;
    }

    public int getQuantidadeMaximaItensPorCiclo() {
        return quantidadeMaximaItensPorCiclo;
    }

    public void setQuantidadeMaximaItensPorCiclo(final int quantidadeMaximaItensPorCiclo) {
        this.quantidadeMaximaItensPorCiclo = quantidadeMaximaItensPorCiclo;
    }

    public int getTimeoutSondagemMillis() {
        return timeoutSondagemMillis;
    }

    public void setTimeoutSondagemMillis(final int timeoutSondagemMillis) {
        this.timeoutSondagemMillis = timeoutSondagemMillis;
    }

    public int getTimeoutEntregaMillis() {
        return timeoutEntregaMillis;
    }

    public void setTimeoutEntregaMillis(final int timeoutEntregaMillis) {
        this.timeoutEntregaMillis = timeoutEntregaMillis;
    }

    public int getTimeoutRecuperacaoProcessamentoSegundos() {
        return timeoutRecuperacaoProcessamentoSegundos;
    }

    public void setTimeoutRecuperacaoProcessamentoSegundos(final int timeoutRecuperacaoProcessamentoSegundos) {
        this.timeoutRecuperacaoProcessamentoSegundos = timeoutRecuperacaoProcessamentoSegundos;
    }
}
