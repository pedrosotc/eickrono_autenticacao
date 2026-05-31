package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eickrono.api.identidade.aplicacao.modelo.CredencialSocialDeclarada;
import com.eickrono.api.identidade.infraestrutura.configuracao.CredenciaisSociaisNativasProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ValidadorCredencialSocialNativaServiceTest {

    private final ValidadorCredencialSocialNativaService service = new ValidadorCredencialSocialNativaService(
            new RestTemplateBuilder(),
            new CredenciaisSociaisNativasProperties()
    );

    @Test
    void rejeitaTokenSocialAusenteAntesDeConsultarProvedor() {
        CredencialSocialDeclarada declarada = new CredencialSocialDeclarada(
                "google-sub-123",
                "social@eickrono.com",
                "social.user",
                "Social User",
                "https://cdn.eickrono.store/avatar-social.png"
        );

        assertThatThrownBy(() -> service.validar("google", " ", declarada))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response = (ResponseStatusException) exception;
                    org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
