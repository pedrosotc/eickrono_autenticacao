package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class CanalEnvioCodigoRecuperacaoSenhaEmailSmtp implements CanalEnvioCodigoRecuperacaoSenhaEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoRecuperacaoSenhaEmailSmtp.class);
    private static final DateTimeFormatter FORMATADOR_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");

    private final JavaMailSender javaMailSender;
    private final CadastroEmailProperties cadastroEmailProperties;

    public CanalEnvioCodigoRecuperacaoSenhaEmailSmtp(final JavaMailSender javaMailSender,
                                                     final CadastroEmailProperties cadastroEmailProperties) {
        this.javaMailSender = Objects.requireNonNull(javaMailSender, "javaMailSender e obrigatorio");
        this.cadastroEmailProperties = Objects.requireNonNull(
                cadastroEmailProperties, "cadastroEmailProperties e obrigatorio");
    }

    @Override
    public void enviar(final RecuperacaoSenha recuperacaoSenha, final String codigo) {
        RecuperacaoSenha recuperacao = Objects.requireNonNull(recuperacaoSenha, "recuperacaoSenha e obrigatoria");
        String codigoConfirmacao = Objects.requireNonNull(codigo, "codigo e obrigatorio").trim();
        if (codigoConfirmacao.isBlank()) {
            throw new IllegalArgumentException("codigo e obrigatorio");
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(recuperacao.getEmailPrincipal());
        mensagem.setFrom(cadastroEmailProperties.getRemetente());
        if (cadastroEmailProperties.getResponderPara() != null
                && !cadastroEmailProperties.getResponderPara().isBlank()) {
            mensagem.setReplyTo(cadastroEmailProperties.getResponderPara());
        }
        mensagem.setSubject(cadastroEmailProperties.getAssuntoRecuperacaoSenha());
        mensagem.setText(criarCorpoMensagem(recuperacao, codigoConfirmacao));

        try {
            javaMailSender.send(mensagem);
            LOGGER.info(
                    "Codigo de recuperacao de senha enviado por SMTP para {} (fluxoId={})",
                    recuperacao.getEmailPrincipal(),
                    recuperacao.getFluxoId()
            );
        } catch (MailException ex) {
            throw new IllegalStateException("Falha ao enviar o codigo de recuperacao de senha por SMTP.", ex);
        }
    }

    private String criarCorpoMensagem(final RecuperacaoSenha recuperacaoSenha, final String codigo) {
        return """
                Ola,%n%n\
                Voce solicitou a recuperacao de senha da sua conta no %s.%n%n\
                Codigo de recuperacao: %s%n\
                Solicitacao: %s%n\
                Validade ate: %s%n%n\
                Se voce nao reconhece esta solicitacao, ignore esta mensagem.%n\
                """
                .formatted(
                        cadastroEmailProperties.getNomeAplicacao(),
                        codigo,
                        recuperacaoSenha.getFluxoId(),
                        FORMATADOR_DATA_HORA.format(recuperacaoSenha.getCodigoEmailExpiraEm())
                );
    }
}
