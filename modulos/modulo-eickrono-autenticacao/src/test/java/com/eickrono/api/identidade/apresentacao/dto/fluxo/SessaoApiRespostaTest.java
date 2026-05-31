package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessaoApiRespostaTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void deveSerializarCamposDeAvatarPreferidoNaSessaoPublica() {
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-05-20T10:15:30-03:00");
        SessaoApiResposta resposta = new SessaoApiResposta(
                true,
                "Bearer",
                "access-token",
                "refresh-token",
                3600L,
                "token-dispositivo",
                atualizadoEm.plusDays(30),
                null,
                null,
                null,
                List.of(),
                "LIBERADO",
                "pedrosotc@example.com",
                "pedrosotc",
                "https://cdn.eickrono.com/avatares/pedrosotc.png",
                "THIMISU",
                "42",
                atualizadoEm,
                false,
                true,
                true
        );

        JsonNode json = objectMapper.valueToTree(resposta);

        assertThat(json.path("usuario").asText()).isEqualTo("pedrosotc");
        assertThat(json.path("avatarPreferidoUrl").asText())
                .isEqualTo("https://cdn.eickrono.com/avatares/pedrosotc.png");
        assertThat(json.path("avatarPreferidoOrigem").asText()).isEqualTo("THIMISU");
        assertThat(json.path("avatarPreferidoVersao").asText()).isEqualTo("42");
        assertThat(json.path("avatarPreferidoAtualizadoEm").asText()).isEqualTo("2026-05-20T10:15:30-03:00");
    }

    @Test
    void deveDesserializarCamposDeAvatarPreferidoNaSessaoPublica() throws Exception {
        String json = """
                {
                  "autenticado": true,
                  "tipoToken": "Bearer",
                  "accessToken": "access-token",
                  "refreshToken": "refresh-token",
                  "expiresIn": 3600,
                  "tokenDispositivo": "token-dispositivo",
                  "tokenDispositivoExpiraEm": "2026-06-19T10:15:30-03:00",
                  "registroDispositivoId": null,
                  "registroDispositivoExpiraEm": null,
                  "statusRegistroDispositivo": null,
                  "canaisConfirmacao": [],
                  "statusUsuario": "LIBERADO",
                  "emailPrincipal": "pedrosotc@example.com",
                  "usuario": "pedrosotc",
                  "avatarPreferidoUrl": "https://cdn.eickrono.com/avatares/pedrosotc.png",
                  "avatarPreferidoOrigem": "THIMISU",
                  "avatarPreferidoVersao": "42",
                  "avatarPreferidoAtualizadoEm": "2026-05-20T10:15:30-03:00",
                  "primeiraSessao": false,
                  "podeOferecerBiometria": true,
                  "podeOferecerVinculacaoSocial": true
                }
                """;

        SessaoApiResposta resposta = objectMapper.readValue(json, SessaoApiResposta.class);

        assertThat(resposta.emailPrincipal()).isEqualTo("pedrosotc@example.com");
        assertThat(resposta.usuario()).isEqualTo("pedrosotc");
        assertThat(resposta.avatarPreferidoUrl())
                .isEqualTo("https://cdn.eickrono.com/avatares/pedrosotc.png");
        assertThat(resposta.avatarPreferidoOrigem()).isEqualTo("THIMISU");
        assertThat(resposta.avatarPreferidoVersao()).isEqualTo("42");
        assertThat(resposta.avatarPreferidoAtualizadoEm())
                .isEqualTo(OffsetDateTime.parse("2026-05-20T10:15:30-03:00"));
    }
}
