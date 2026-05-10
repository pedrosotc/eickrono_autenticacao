package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class CanalEnvioCodigoCadastroEmailSmtp implements CanalEnvioCodigoCadastroEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoCadastroEmailSmtp.class);
    private static final DateTimeFormatter FORMATADOR_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");

    private final JavaMailSender javaMailSender;
    private final CadastroEmailProperties cadastroEmailProperties;

    public CanalEnvioCodigoCadastroEmailSmtp(final JavaMailSender javaMailSender,
                                             final CadastroEmailProperties cadastroEmailProperties) {
        this.javaMailSender = Objects.requireNonNull(javaMailSender, "javaMailSender e obrigatorio");
        this.cadastroEmailProperties = Objects.requireNonNull(
                cadastroEmailProperties, "cadastroEmailProperties e obrigatorio");
    }

    @Override
    public void enviar(final CadastroConta cadastroConta, final String codigo) {
        CadastroConta cadastro = Objects.requireNonNull(cadastroConta, "cadastroConta e obrigatorio");
        String codigoConfirmacao = Objects.requireNonNull(codigo, "codigo e obrigatorio").trim();
        if (codigoConfirmacao.isBlank()) {
            throw new IllegalArgumentException("codigo e obrigatorio");
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(cadastro.getEmailPrincipal());
        mensagem.setFrom(cadastroEmailProperties.getRemetente());
        if (cadastroEmailProperties.getResponderPara() != null
                && !cadastroEmailProperties.getResponderPara().isBlank()) {
            mensagem.setReplyTo(cadastroEmailProperties.getResponderPara());
        }
        mensagem.setSubject(cadastroEmailProperties.getAssunto());
        mensagem.setText(criarCorpoMensagem(cadastro, codigoConfirmacao));

        try {
            javaMailSender.send(mensagem);
            LOGGER.info(
                    "Codigo de confirmacao de cadastro enviado por SMTP para {} (cadastroId={}, sistema={})",
                    cadastro.getEmailPrincipal(),
                    cadastro.getCadastroId(),
                    cadastro.getSistemaSolicitante()
            );
        } catch (MailException ex) {
            throw new IllegalStateException("Falha ao enviar o codigo de confirmacao do cadastro por SMTP.", ex);
        }
    }

    private String criarCorpoMensagem(final CadastroConta cadastroConta, final String codigo) {
        return """
                Ola,%n%n\
                Voce solicitou a confirmacao de cadastro da sua conta no %s.%n%n\
                Codigo de confirmacao: %s%n\
                Cadastro: %s%n\
                Validade ate: %s%n%n\
                Se voce nao reconhece esta solicitacao, ignore esta mensagem.%n\
                """
                .formatted(
                        cadastroEmailProperties.getNomeAplicacao(),
                        codigo,
                        cadastroConta.getCadastroId(),
                        FORMATADOR_DATA_HORA.format(cadastroConta.getCodigoEmailExpiraEm())
                );
    }
}
