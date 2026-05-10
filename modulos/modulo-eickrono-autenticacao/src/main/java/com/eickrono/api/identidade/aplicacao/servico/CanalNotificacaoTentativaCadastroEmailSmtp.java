package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class CanalNotificacaoTentativaCadastroEmailSmtp implements CanalNotificacaoTentativaCadastroEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalNotificacaoTentativaCadastroEmailSmtp.class);

    private final JavaMailSender javaMailSender;
    private final CadastroEmailProperties cadastroEmailProperties;

    public CanalNotificacaoTentativaCadastroEmailSmtp(final JavaMailSender javaMailSender,
                                                      final CadastroEmailProperties cadastroEmailProperties) {
        this.javaMailSender = Objects.requireNonNull(javaMailSender, "javaMailSender e obrigatorio");
        this.cadastroEmailProperties = Objects.requireNonNull(
                cadastroEmailProperties, "cadastroEmailProperties e obrigatorio");
    }

    @Override
    public void notificar(final String emailPrincipal) {
        String email = Objects.requireNonNull(emailPrincipal, "emailPrincipal e obrigatorio").trim();
        if (email.isBlank()) {
            throw new IllegalArgumentException("emailPrincipal e obrigatorio");
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(email);
        mensagem.setFrom(cadastroEmailProperties.getRemetente());
        if (cadastroEmailProperties.getResponderPara() != null
                && !cadastroEmailProperties.getResponderPara().isBlank()) {
            mensagem.setReplyTo(cadastroEmailProperties.getResponderPara());
        }
        mensagem.setSubject(cadastroEmailProperties.getAssuntoTentativaCadastroEmailExistente());
        mensagem.setText(criarCorpoMensagem());

        try {
            javaMailSender.send(mensagem);
            LOGGER.info("Aviso de tentativa de cadastro enviado por SMTP para {}", email);
        } catch (MailException ex) {
            throw new IllegalStateException("Falha ao enviar o aviso de tentativa de cadastro por SMTP.", ex);
        }
    }

    private String criarCorpoMensagem() {
        return """
                Ola,%n%n\
                Recebemos uma tentativa de criar um novo cadastro utilizando este endereco de e-mail no %s.%n%n\
                Se foi voce, tente entrar com sua conta atual ou utilize o fluxo de recuperação de senha.%n\
                Se nao foi voce, ignore esta mensagem.%n\
                """
                .formatted(cadastroEmailProperties.getNomeAplicacao());
    }
}
