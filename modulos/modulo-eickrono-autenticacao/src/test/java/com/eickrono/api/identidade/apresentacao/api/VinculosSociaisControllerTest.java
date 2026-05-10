package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.servico.VinculoSocialService;
import com.eickrono.api.identidade.apresentacao.dto.AtualizarAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoSenhaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VincularRedeSocialApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VinculoSocialDto;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class VinculosSociaisControllerTest {

    @Mock
    private VinculoSocialService vinculoSocialService;

    private VinculosSociaisController controller;

    @BeforeEach
    void setUp() {
        controller = new VinculosSociaisController(vinculoSocialService);
    }

    @Test
    void deveDelegarListagemComAplicacaoId() {
        Jwt jwt = jwt();
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.listar(jwt, "eickrono-thimisu-app")).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.listar(jwt, "eickrono-thimisu-app");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).listar(jwt, "eickrono-thimisu-app");
    }

    @Test
    void deveDelegarVinculacaoComContextoSocialPendente() {
        Jwt jwt = jwt();
        UUID contextoSocialPendenteId = UUID.fromString("c08d4efb-ea10-4551-a376-4f4fcb4c10d7");
        VincularRedeSocialApiRequest request = new VincularRedeSocialApiRequest(
                "token-externo-google",
                contextoSocialPendenteId,
                "eickrono-thimisu-app",
                "Pessoa Google",
                "https://cdn.eickrono.test/google.png"
        );
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.vincular(
                jwt,
                "google",
                "token-externo-google",
                contextoSocialPendenteId,
                "eickrono-thimisu-app",
                "Pessoa Google",
                "https://cdn.eickrono.test/google.png"
        )).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.vincular("google", request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).vincular(
                jwt,
                "google",
                "token-externo-google",
                contextoSocialPendenteId,
                "eickrono-thimisu-app",
                "Pessoa Google",
                "https://cdn.eickrono.test/google.png"
        );
    }

    @Test
    void deveDelegarSincronizacaoComAplicacaoIdEContextoSocialPendente() {
        Jwt jwt = jwt();
        UUID contextoSocialPendenteId = UUID.fromString("6176fc7d-d619-4579-839f-a3744a03ce4e");
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.sincronizar(
                jwt,
                "google",
                contextoSocialPendenteId,
                "eickrono-thimisu-app"
        )).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.sincronizar(
                "google",
                contextoSocialPendenteId,
                "eickrono-thimisu-app",
                jwt
        );

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).sincronizar(
                jwt,
                "google",
                contextoSocialPendenteId,
                "eickrono-thimisu-app"
        );
    }

    @Test
    void deveDelegarRemocaoComSenhaConfirmacaoEAplicacaoId() {
        Jwt jwt = jwt();
        ConfirmacaoSenhaApiRequest request = new ConfirmacaoSenhaApiRequest("SenhaAtual123");
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.remover(
                jwt,
                "google",
                "SenhaAtual123",
                "eickrono-thimisu-app"
        )).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.remover(
                "google",
                "eickrono-thimisu-app",
                request,
                jwt
        );

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).remover(
                jwt,
                "google",
                "SenhaAtual123",
                "eickrono-thimisu-app"
        );
    }

    @Test
    void deveDelegarAtualizacaoDoAvatarPreferido() {
        Jwt jwt = jwt();
        AtualizarAvatarPreferidoApiRequest request = new AtualizarAvatarPreferidoApiRequest(
                "eickrono-thimisu-app",
                "SOCIAL",
                "google",
                null
        );
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.atualizarAvatarPreferido(jwt, request)).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.atualizarAvatarPreferido(request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).atualizarAvatarPreferido(jwt, request);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub-123")
                .claim("email", "teste@eickrono.com")
                .claim("name", "Pessoa Teste")
                .build();
    }

    private VinculosSociaisDto resposta() {
        return new VinculosSociaisDto(
                List.of(new VinculoSocialDto(
                        "google",
                        true,
                        true,
                        OffsetDateTime.parse("2026-03-11T18:00:00Z"),
                        "t***@gmail.com",
                        "Pessoa Google",
                        "https://cdn.eickrono.test/google.png",
                        OffsetDateTime.parse("2026-03-11T18:00:00Z"),
                        true,
                        "DISPONIVEL",
                        null
                )),
                "SOCIAL",
                "https://cdn.eickrono.test/google.png"
        );
    }
}
