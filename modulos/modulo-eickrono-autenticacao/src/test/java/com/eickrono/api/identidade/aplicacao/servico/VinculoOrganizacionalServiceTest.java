package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.apresentacao.dto.VinculoOrganizacionalDto;
import com.eickrono.api.identidade.apresentacao.dto.VinculosOrganizacionaisDto;
import com.eickrono.api.identidade.dominio.modelo.VinculoOrganizacional;
import com.eickrono.api.identidade.dominio.repositorio.VinculoOrganizacionalRepositorio;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class VinculoOrganizacionalServiceTest {

    @Mock
    private VinculoOrganizacionalRepositorio vinculoOrganizacionalRepositorio;

    @Mock
    private ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService;

    @InjectMocks
    private VinculoOrganizacionalService vinculoOrganizacionalService;

    @Test
    @DisplayName("listar vínculos organizacionais: deve retornar os vínculos do usuário autenticado")
    void deveListarVinculosOrganizacionaisDoUsuarioAutenticado() {
        Jwt jwt = jwt("sub-123");
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("sub-123"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        10L,
                        "sub-123",
                        "jane@empresa.test",
                        "Jane Doe",
                        "usuario-001",
                        "ATIVO"
                )));
        when(vinculoOrganizacionalRepositorio.findAllByPerfilSistemaIdOrderByCriadoEmAsc("usuario-001"))
                .thenReturn(List.of(
                        new VinculoOrganizacional(
                                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                10L,
                                "usuario-001",
                                "org-acme",
                                "Acme Educacao",
                                "ORG-ACME-2026",
                                "jane@empresa.test",
                                true,
                                OffsetDateTime.parse("2026-04-01T10:00:00Z")
                        ),
                        new VinculoOrganizacional(
                                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                10L,
                                "usuario-001",
                                "org-beta",
                                "Beta Labs",
                                "ORG-BETA-2026",
                                null,
                                false,
                                OffsetDateTime.parse("2026-04-02T10:00:00Z")
                        )
                ));

        VinculosOrganizacionaisDto resposta = vinculoOrganizacionalService.listar(jwt);

        assertThat(resposta.vinculos())
                .extracting(
                        VinculoOrganizacionalDto::organizacaoId,
                        VinculoOrganizacionalDto::nomeOrganizacao,
                        VinculoOrganizacionalDto::conviteCodigo,
                        VinculoOrganizacionalDto::emailConvidado,
                        VinculoOrganizacionalDto::exigeContaSeparada
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "org-acme",
                                "Acme Educacao",
                                "ORG-ACME-2026",
                                "jane@empresa.test",
                                true
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "org-beta",
                                "Beta Labs",
                                "ORG-BETA-2026",
                                null,
                                false
                        )
                );
    }

    @Test
    @DisplayName("listar vínculos organizacionais: deve retornar vazio quando o contexto local ainda não existir")
    void deveRetornarVazioQuandoContextoLocalNaoExistir() {
        Jwt jwt = jwt("sub-inexistente");
        when(resolvedorContextoAutenticacaoService.buscarPorSubPublico("sub-inexistente"))
                .thenReturn(Optional.empty());

        VinculosOrganizacionaisDto resposta = vinculoOrganizacionalService.listar(jwt);

        assertThat(resposta.vinculos()).isEmpty();
        verify(resolvedorContextoAutenticacaoService).buscarPorSubPublico("sub-inexistente");
    }

    private Jwt jwt(final String sub) {
        return Jwt.withTokenValue("token")
                .subject(sub)
                .header("alg", "none")
                .claim("scope", "vinculos:ler")
                .build();
    }
}
