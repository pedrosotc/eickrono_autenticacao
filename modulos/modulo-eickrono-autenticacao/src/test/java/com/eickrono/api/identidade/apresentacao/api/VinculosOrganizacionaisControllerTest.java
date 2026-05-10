package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.servico.VinculoOrganizacionalService;
import com.eickrono.api.identidade.apresentacao.dto.VinculoOrganizacionalDto;
import com.eickrono.api.identidade.apresentacao.dto.VinculosOrganizacionaisDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class VinculosOrganizacionaisControllerTest {

    @Mock
    private VinculoOrganizacionalService vinculoOrganizacionalService;

    private VinculosOrganizacionaisController controller;

    @BeforeEach
    void setUp() {
        controller = new VinculosOrganizacionaisController(vinculoOrganizacionalService);
    }

    @Test
    void deveDelegarListagem() {
        Jwt jwt = jwt();
        VinculosOrganizacionaisDto respostaEsperada = new VinculosOrganizacionaisDto(List.of(
                new VinculoOrganizacionalDto(
                        "org-acme",
                        "Acme Educacao",
                        "ORG-ACME-2026",
                        "jane@empresa.test",
                        true,
                        OffsetDateTime.parse("2026-04-01T10:00:00Z")
                )
        ));
        when(vinculoOrganizacionalService.listar(jwt)).thenReturn(respostaEsperada);

        ResponseEntity<VinculosOrganizacionaisDto> resposta = controller.listar(jwt);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(vinculoOrganizacionalService).listar(jwt);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub-123")
                .claim("email", "teste@eickrono.com")
                .build();
    }
}
