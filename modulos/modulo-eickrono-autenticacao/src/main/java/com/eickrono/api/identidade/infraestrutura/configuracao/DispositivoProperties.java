package com.eickrono.api.identidade.infraestrutura.configuracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades relacionadas ao fluxo de registro de dispositivos.
 */
@ConfigurationProperties(prefix = "identidade.dispositivo")
public class DispositivoProperties {

    private final Token token = new Token();
    private final Codigo codigo = new Codigo();
    private final Onboarding onboarding = new Onboarding();
    private final Offline offline = new Offline();

    public Token getToken() {
        return token;
    }

    public Codigo getCodigo() {
        return codigo;
    }

    public Onboarding getOnboarding() {
        return onboarding;
    }

    public Offline getOffline() {
        return offline;
    }

    public static class Token {
        /**
         * Segredo usado para assinar o token (HMAC).
         */
        private String segredoHmac = "change-me-token";
        /**
         * Tamanho do token em bytes antes de codificar em Base64.
         */
        private int tamanhoBytes = 32;
        /**
         * Validade padrão do token em horas.
         */
        private long validadeHoras = 720;

        public String getSegredoHmac() {
            return segredoHmac;
        }

        public void setSegredoHmac(String segredoHmac) {
            this.segredoHmac = segredoHmac;
        }

        public int getTamanhoBytes() {
            return tamanhoBytes;
        }

        public void setTamanhoBytes(int tamanhoBytes) {
            this.tamanhoBytes = tamanhoBytes;
        }

        public long getValidadeHoras() {
            return validadeHoras;
        }

        public void setValidadeHoras(long validadeHoras) {
            this.validadeHoras = validadeHoras;
        }
    }

    public static class Codigo {
        /**
         * Segredo usado para gerar o hash dos códigos (HMAC).
         */
        private String segredoHmac = "change-me-codigo";
        private int tamanho = 6;
        private int tentativasMaximas = 5;
        private int reenviosMaximos = 3;
        private long expiracaoHoras = 9;

        public String getSegredoHmac() {
            return segredoHmac;
        }

        public void setSegredoHmac(String segredoHmac) {
            this.segredoHmac = segredoHmac;
        }

        public int getTamanho() {
            return tamanho;
        }

        public void setTamanho(int tamanho) {
            this.tamanho = tamanho;
        }

        public int getTentativasMaximas() {
            return tentativasMaximas;
        }

        public void setTentativasMaximas(int tentativasMaximas) {
            this.tentativasMaximas = tentativasMaximas;
        }

        public int getReenviosMaximos() {
            return reenviosMaximos;
        }

        public void setReenviosMaximos(int reenviosMaximos) {
            this.reenviosMaximos = reenviosMaximos;
        }

        public long getExpiracaoHoras() {
            return expiracaoHoras;
        }

        public void setExpiracaoHoras(long expiracaoHoras) {
            this.expiracaoHoras = expiracaoHoras;
        }
    }

    public static class Onboarding {
        private boolean smsHabilitado = false;
        private String smsFornecedor = "log";

        public boolean isSmsHabilitado() {
            return smsHabilitado;
        }

        public void setSmsHabilitado(boolean smsHabilitado) {
            this.smsHabilitado = smsHabilitado;
        }

        public String getSmsFornecedor() {
            return smsFornecedor;
        }

        public void setSmsFornecedor(String smsFornecedor) {
            this.smsFornecedor = smsFornecedor;
        }
    }

    public static class Offline {
        private boolean permitido = true;
        private long tempoMaximoMinutos = 43200;
        private boolean exigeReconciliacao = true;
        private boolean bloquearQuandoTokenRevogado = true;
        private boolean bloquearQuandoTokenExpirado = true;
        private boolean bloquearQuandoDispositivoSemConfianca = true;
        private java.util.List<String> eventosPermitidos = new java.util.ArrayList<>(java.util.List.of(
                "MODO_OFFLINE_ATIVADO",
                "MODO_OFFLINE_ENCERRADO",
                "SESSAO_EXPIRADA_OFFLINE",
                "RECONCILIACAO_REALIZADA",
                "SOBREPOSICAO_DE_USO_REPORTADA"
        ));

        public boolean isPermitido() {
            return permitido;
        }

        public void setPermitido(boolean permitido) {
            this.permitido = permitido;
        }

        public long getTempoMaximoMinutos() {
            return tempoMaximoMinutos;
        }

        public void setTempoMaximoMinutos(long tempoMaximoMinutos) {
            this.tempoMaximoMinutos = tempoMaximoMinutos;
        }

        public boolean isExigeReconciliacao() {
            return exigeReconciliacao;
        }

        public void setExigeReconciliacao(boolean exigeReconciliacao) {
            this.exigeReconciliacao = exigeReconciliacao;
        }

        public boolean isBloquearQuandoTokenRevogado() {
            return bloquearQuandoTokenRevogado;
        }

        public void setBloquearQuandoTokenRevogado(boolean bloquearQuandoTokenRevogado) {
            this.bloquearQuandoTokenRevogado = bloquearQuandoTokenRevogado;
        }

        public boolean isBloquearQuandoTokenExpirado() {
            return bloquearQuandoTokenExpirado;
        }

        public void setBloquearQuandoTokenExpirado(boolean bloquearQuandoTokenExpirado) {
            this.bloquearQuandoTokenExpirado = bloquearQuandoTokenExpirado;
        }

        public boolean isBloquearQuandoDispositivoSemConfianca() {
            return bloquearQuandoDispositivoSemConfianca;
        }

        public void setBloquearQuandoDispositivoSemConfianca(boolean bloquearQuandoDispositivoSemConfianca) {
            this.bloquearQuandoDispositivoSemConfianca = bloquearQuandoDispositivoSemConfianca;
        }

        public java.util.List<String> getEventosPermitidos() {
            return eventosPermitidos;
        }

        public void setEventosPermitidos(java.util.List<String> eventosPermitidos) {
            this.eventosPermitidos = eventosPermitidos;
        }
    }
}
