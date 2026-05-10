package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoCadastroEmail;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoCadastroEmailLog;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoCadastroEmailSmtp;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoRecuperacaoSenhaEmail;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoRecuperacaoSenhaEmailLog;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoRecuperacaoSenhaEmailSmtp;
import com.eickrono.api.identidade.aplicacao.servico.CanalNotificacaoTentativaCadastroEmail;
import com.eickrono.api.identidade.aplicacao.servico.CanalNotificacaoTentativaCadastroEmailLog;
import com.eickrono.api.identidade.aplicacao.servico.CanalNotificacaoTentativaCadastroEmailSmtp;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class CadastroEmailConfiguracao {

    @Bean
    public CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail(
            final CadastroEmailProperties cadastroEmailProperties,
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        Objects.requireNonNull(cadastroEmailProperties, "cadastroEmailProperties e obrigatorio");
        String fornecedor = Objects.requireNonNullElse(cadastroEmailProperties.getFornecedor(), "log")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (fornecedor) {
            case "log" -> new CanalEnvioCodigoCadastroEmailLog();
            case "smtp" -> new CanalEnvioCodigoCadastroEmailSmtp(
                    obterJavaMailSenderObrigatorio(javaMailSenderProvider),
                    cadastroEmailProperties
            );
            default -> throw new IllegalStateException(
                    "Fornecedor de e-mail do cadastro invalido: " + cadastroEmailProperties.getFornecedor());
        };
    }

    @Bean
    public CanalEnvioCodigoRecuperacaoSenhaEmail canalEnvioCodigoRecuperacaoSenhaEmail(
            final CadastroEmailProperties cadastroEmailProperties,
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        Objects.requireNonNull(cadastroEmailProperties, "cadastroEmailProperties e obrigatorio");
        String fornecedor = Objects.requireNonNullElse(cadastroEmailProperties.getFornecedor(), "log")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (fornecedor) {
            case "log" -> new CanalEnvioCodigoRecuperacaoSenhaEmailLog();
            case "smtp" -> new CanalEnvioCodigoRecuperacaoSenhaEmailSmtp(
                    obterJavaMailSenderObrigatorio(javaMailSenderProvider),
                    cadastroEmailProperties
            );
            default -> throw new IllegalStateException(
                    "Fornecedor de e-mail do cadastro invalido: " + cadastroEmailProperties.getFornecedor());
        };
    }

    @Bean
    public CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail(
            final CadastroEmailProperties cadastroEmailProperties,
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        Objects.requireNonNull(cadastroEmailProperties, "cadastroEmailProperties e obrigatorio");
        String fornecedor = Objects.requireNonNullElse(cadastroEmailProperties.getFornecedor(), "log")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (fornecedor) {
            case "log" -> new CanalNotificacaoTentativaCadastroEmailLog();
            case "smtp" -> new CanalNotificacaoTentativaCadastroEmailSmtp(
                    obterJavaMailSenderObrigatorio(javaMailSenderProvider),
                    cadastroEmailProperties
            );
            default -> throw new IllegalStateException(
                    "Fornecedor de e-mail do cadastro invalido: " + cadastroEmailProperties.getFornecedor());
        };
    }

    private static JavaMailSender obterJavaMailSenderObrigatorio(
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException(
                    "Fornecedor SMTP configurado, mas nenhum JavaMailSender foi inicializado. "
                            + "Revise spring.mail.host, spring.mail.port e credenciais.");
        }
        return javaMailSender;
    }
}
