package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.servico.VinculoSocialService;
import com.eickrono.api.identidade.apresentacao.dto.AtualizarAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.UploadAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AvatarPreferidoControllerTest {

    @Mock
    private VinculoSocialService vinculoSocialService;

    private AvatarPreferidoController controller;

    @BeforeEach
    void setUp() {
        controller = new AvatarPreferidoController(vinculoSocialService);
    }

    @Test
    void deveDelegarAtualizacaoDoAvatarPreferidoPelaRotaCanonicaDaConta() {
        Jwt jwt = jwt();
        AtualizarAvatarPreferidoApiRequest request = new AtualizarAvatarPreferidoApiRequest(
                "eickrono-thimisu-app",
                "SOCIAL",
                "google",
                null
        );
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.atualizarAvatarPreferido(jwt, request)).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.atualizar(request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).atualizarAvatarPreferido(jwt, request);
    }

    @Test
    void deveDelegarUploadDoAvatarPreferidoPelaRotaCanonicaDaConta() {
        Jwt jwt = jwt();
        UploadAvatarPreferidoApiRequest request = new UploadAvatarPreferidoApiRequest(
                "eickrono-thimisu-app",
                "avatar.jpg",
                "image/jpeg",
                3L,
                "YWJj"
        );
        VinculosSociaisDto respostaEsperada = resposta();
        when(vinculoSocialService.uploadAvatarPreferido(jwt, request)).thenReturn(respostaEsperada);

        ResponseEntity<VinculosSociaisDto> resposta = controller.upload(request, jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoSocialService).uploadAvatarPreferido(jwt, request);
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
                List.of(),
                "URL_EXTERNA",
                "https://cdn.eickrono.test/avatar.jpg"
        );
    }
}
