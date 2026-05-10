package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class CanalNotificacaoTentativaCadastroEmailSmtpTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> mensagemCaptor;

    @Test
    @DisplayName("deve montar e enviar o aviso de tentativa de cadastro para e-mail já vinculado")
    void deveEnviarAvisoPorSmtp() {
        CadastroEmailProperties properties = new CadastroEmailProperties();
        properties.setFornecedor("smtp");
        properties.setRemetente("nao-responda@eickrono.com");
        properties.setResponderPara("suporte@eickrono.com");
        properties.setAssuntoTentativaCadastroEmailExistente("Tentativa de cadastro detectada");
        properties.setNomeAplicacao("Eickrono Thimisu");

        CanalNotificacaoTentativaCadastroEmailSmtp canal =
                new CanalNotificacaoTentativaCadastroEmailSmtp(javaMailSender, properties);

        canal.notificar("ana@eickrono.com");

        verify(javaMailSender).send(mensagemCaptor.capture());
        SimpleMailMessage mensagem = mensagemCaptor.getValue();
        assertThat(mensagem.getTo()).containsExactly("ana@eickrono.com");
        assertThat(mensagem.getFrom()).isEqualTo("nao-responda@eickrono.com");
        assertThat(mensagem.getReplyTo()).isEqualTo("suporte@eickrono.com");
        assertThat(mensagem.getSubject()).isEqualTo("Tentativa de cadastro detectada");
        assertThat(mensagem.getText()).contains("Eickrono Thimisu");
        assertThat(mensagem.getText()).contains("recuperação de senha");
    }
}
