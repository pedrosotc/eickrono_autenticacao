package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro;
import com.eickrono.api.identidade.dominio.modelo.TipoComprovanteAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CadastroApiRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void deveSerializarVinculosSociaisConfirmadosSemContextoPendente() {
        CadastroApiRequest request = new CadastroApiRequest(
                "eickrono-thimisu-app",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                SexoPessoaCadastro.FEMININO,
                "BR",
                LocalDate.parse("1994-08-17"),
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "SenhaForte123",
                "SenhaForte123",
                true,
                true,
                PlataformaAtestacaoApp.IOS,
                List.of(
                        new VinculoSocialConfirmadoApiRequest(
                                "google",
                                "google-user-123",
                                "ana.google",
                                "ana.social@example.com",
                                "Ana Social",
                                "https://cdn.test/avatar-google.png",
                                true
                        ),
                        new VinculoSocialConfirmadoApiRequest(
                                "apple",
                                "apple-user-456",
                                "ana.apple",
                                null,
                                null,
                                "https://cdn.test/avatar-apple.png",
                                false
                        )
                ),
                null,
                new AtestacaoOperacaoApiRequest(
                        PlataformaAtestacaoApp.IOS,
                        ProvedorAtestacaoApp.APPLE_APP_ATTEST,
                        TipoComprovanteAtestacaoApp.OBJETO_ASSERCAO,
                        "desafio",
                        "ZGVzYWZpbw==",
                        "Y29tcHJvdmFudGU=",
                        OffsetDateTime.parse("2026-03-26T20:00:00Z"),
                        "chave"
                ),
                new SegurancaAplicativoApiRequest(
                        "IOS",
                        "APPLE_APP_ATTEST",
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        List.of(),
                        0,
                        null,
                        "com.eickrono.thimisu",
                        "TEAM123",
                        null
                )
        );

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.has("vinculoSocialConfirmado")).isFalse();
        assertThat(json.has("avatarCadastroConfirmado")).isFalse();
        assertThat(json.path("vinculosSociaisConfirmados")).hasSize(2);
        assertThat(json.path("vinculosSociaisConfirmados").get(0).path("provedor").asText())
                .isEqualTo("google");
        assertThat(json.path("vinculosSociaisConfirmados").get(0).path("avatarPreferido").asBoolean())
                .isTrue();
    }
}
