package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
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
class CanalEnvioCodigoCadastroEmailSmtpTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> mensagemCaptor;

    @Test
    @DisplayName("deve montar e enviar o e-mail de confirmacao do cadastro por SMTP")
    void deveEnviarEmailPorSmtp() {
        CadastroEmailProperties properties = new CadastroEmailProperties();
        properties.setFornecedor("smtp");
        properties.setRemetente("nao-responda@eickrono.com");
        properties.setResponderPara("suporte@eickrono.com");
        properties.setAssunto("Confirme seu cadastro");
        properties.setNomeAplicacao("Eickrono Thimisu");

        CanalEnvioCodigoCadastroEmailSmtp canal = new CanalEnvioCodigoCadastroEmailSmtp(javaMailSender, properties);
        CadastroConta cadastro = new CadastroConta(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sub-teste",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash",
                OffsetDateTime.of(2026, 3, 19, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 3, 19, 19, 0, 0, 0, ZoneOffset.UTC),
                "identidade-servidor",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.of(2026, 3, 19, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 3, 19, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        canal.enviar(cadastro, "123456");

        verify(javaMailSender).send(mensagemCaptor.capture());
        SimpleMailMessage mensagem = mensagemCaptor.getValue();
        assertThat(mensagem.getTo()).containsExactly("ana@eickrono.com");
        assertThat(mensagem.getFrom()).isEqualTo("nao-responda@eickrono.com");
        assertThat(mensagem.getReplyTo()).isEqualTo("suporte@eickrono.com");
        assertThat(mensagem.getSubject()).isEqualTo("Confirme seu cadastro");
        assertThat(mensagem.getText()).contains("123456");
        assertThat(mensagem.getText()).contains("Eickrono Thimisu");
        assertThat(mensagem.getText()).contains("11111111-1111-1111-1111-111111111111");
    }
}
