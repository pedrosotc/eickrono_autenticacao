package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Desafio efêmero emitido pelo backend para vincular a prova nativa a uma operação sensível.
 */
@Entity
@Table(name = "atestacoes_app_desafios")
public class DesafioAtestacaoApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identificador_desafio", nullable = false, unique = true, length = 36)
    private String identificadorDesafio;

    @Column(name = "desafio_base64", nullable = false, unique = true, length = 128)
    private String desafioBase64;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OperacaoAtestacaoApp operacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlataformaAtestacaoApp plataforma;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor_esperado", nullable = false, length = 50)
    private ProvedorAtestacaoApp provedorEsperado;

    @Column(name = "usuario_sub", length = 255)
    private String usuarioSub;

    @Column(name = "pessoa_id_perfil")
    private Long pessoaIdPerfil;

    @Column(name = "cadastro_id")
    private UUID cadastroId;

    @Column(name = "registro_dispositivo_id")
    private UUID registroDispositivoId;

    @Column(name = "ip_solicitante", length = 64)
    private String ipSolicitante;

    @Column(name = "user_agent_solicitante", length = 512)
    private String userAgentSolicitante;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "consumido_em")
    private OffsetDateTime consumidoEm;

    protected DesafioAtestacaoApp() {
        // Construtor protegido para JPA.
    }

    public DesafioAtestacaoApp(final String identificadorDesafio,
                               final String desafioBase64,
                               final OperacaoAtestacaoApp operacao,
                               final PlataformaAtestacaoApp plataforma,
                               final String usuarioSub,
                               final Long pessoaIdPerfil,
                               final UUID cadastroId,
                               final UUID registroDispositivoId,
                               final String ipSolicitante,
                               final String userAgentSolicitante,
                               final OffsetDateTime criadoEm,
                               final OffsetDateTime expiraEm) {
        this.identificadorDesafio = Objects.requireNonNull(identificadorDesafio, "identificadorDesafio é obrigatório");
        this.desafioBase64 = Objects.requireNonNull(desafioBase64, "desafioBase64 é obrigatório");
        this.operacao = Objects.requireNonNull(operacao, "operacao é obrigatória");
        this.plataforma = Objects.requireNonNull(plataforma, "plataforma é obrigatória");
        this.provedorEsperado = Objects.requireNonNull(plataforma.getProvedorPadrao(), "provedorEsperado é obrigatório");
        this.usuarioSub = normalizar(usuarioSub, 255);
        this.pessoaIdPerfil = pessoaIdPerfil;
        this.cadastroId = cadastroId;
        this.registroDispositivoId = registroDispositivoId;
        this.ipSolicitante = normalizar(ipSolicitante, 64);
        this.userAgentSolicitante = normalizar(userAgentSolicitante, 512);
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
    }

    public Long getId() {
        return id;
    }

    public String getIdentificadorDesafio() {
        return identificadorDesafio;
    }

    public String getDesafioBase64() {
        return desafioBase64;
    }

    public OperacaoAtestacaoApp getOperacao() {
        return operacao;
    }

    public PlataformaAtestacaoApp getPlataforma() {
        return plataforma;
    }

    public ProvedorAtestacaoApp getProvedorEsperado() {
        return provedorEsperado;
    }

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public Long getPessoaIdPerfil() {
        return pessoaIdPerfil;
    }

    public UUID getCadastroId() {
        return cadastroId;
    }

    public UUID getRegistroDispositivoId() {
        return registroDispositivoId;
    }

    public String getIpSolicitante() {
        return ipSolicitante;
    }

    public String getUserAgentSolicitante() {
        return userAgentSolicitante;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public OffsetDateTime getConsumidoEm() {
        return consumidoEm;
    }

    public boolean estaExpirado(final OffsetDateTime referencia) {
        return !expiraEm.isAfter(Objects.requireNonNull(referencia, "referencia é obrigatória"));
    }

    public boolean estaConsumido() {
        return consumidoEm != null;
    }

    public void marcarConsumido(final OffsetDateTime momento) {
        this.consumidoEm = Objects.requireNonNull(momento, "momento é obrigatório");
    }

    private static String normalizar(final String valor, final int limite) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.length() <= limite ? valor : valor.substring(0, limite);
    }
}
