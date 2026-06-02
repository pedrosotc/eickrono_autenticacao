package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExclusaoCadastroProdutoDryRunServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private ExclusaoCadastroProdutoDryRunService service;

    @BeforeEach
    void setUp() {
        service = new ExclusaoCadastroProdutoDryRunService(jdbcTemplate, List.of(), new ObjectMapper());
    }

    @Test
    void deveRecusarExecucaoSemDryRun() {
        ExclusaoCadastroProdutoApiRequest request = new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                false,
                "teste",
                null
        );

        assertThatThrownBy(() -> service.simular(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("servico de execucao");
    }

    @Test
    void deveBloquearQuandoProdutoNaoExiste() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());

        var resposta = service.simular(new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "teste",
                null
        ));

        assertThat(resposta.dryRun()).isTrue();
        assertThat(resposta.bloqueios())
                .anySatisfy(bloqueio -> assertThat(bloqueio.codigo()).isEqualTo("produto_nao_encontrado"));
        assertThat(resposta.acoes()).isEmpty();
    }

    @Test
    void deveMontarPlanoComVinculoResolvidoSemExecutarDelete() {
        UUID usuarioId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        List<Map<String, Object>> produtoResolvido = List.of(Map.of("id", 1L, "codigo", "THIMISU"));
        List<Map<String, Object>> vinculoResolvido =
                List.of(Map.of("usuario_id", usuarioId, "vinculo_id", vinculoId));
        List<Map<String, Object>> usuarioKeycloakResolvido =
                List.of(Map.of("id", usuarioId, "sub_remoto", "sub-keycloak-1"));
        List<Map<String, Object>> pessoaResolvida = List.of(Map.of("pessoa_id", pessoaId));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        produtoResolvido,
                        vinculoResolvido,
                        usuarioKeycloakResolvido,
                        pessoaResolvida
                );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L, 1L, 1L, 2L, 1L, 0L, 1L, 1L, 1L, 1L, 2L, 1L, 2L);

        var resposta = service.simular(new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "teste",
                null
        ));

        assertThat(resposta.alvosResolvidos().usuariosAutenticacaoIds()).containsExactly(usuarioId.toString());
        assertThat(resposta.alvosResolvidos().vinculosProdutoIds()).containsExactly(vinculoId.toString());
        assertThat(resposta.acoes())
                .anySatisfy(acao -> {
                    assertThat(acao.recurso()).isEqualTo("autenticacao.usuarios_clientes_ecossistema");
                    assertThat(acao.quantidade()).isEqualTo(1L);
                })
                .anySatisfy(acao -> {
                    assertThat(acao.recurso()).isEqualTo("autenticacao.usuarios_formas_acesso");
                    assertThat(acao.quantidade()).isEqualTo(2L);
                })
                .anySatisfy(acao -> {
                    assertThat(acao.recurso()).isEqualTo("auditoria.usuarios_clientes_ecossistema_historico");
                    assertThat(acao.tipo()).isEqualTo("ANONIMIZAR");
                    assertThat(acao.quantidade()).isEqualTo(1L);
                })
                .anySatisfy(acao -> {
                    assertThat(acao.recurso()).isEqualTo("auditoria.usuarios_historico");
                    assertThat(acao.tipo()).isEqualTo("ANONIMIZAR");
                    assertThat(acao.quantidade()).isEqualTo(2L);
                })
                .anySatisfy(acao -> {
                    assertThat(acao.sistema()).isEqualTo("KEYCLOAK");
                    assertThat(acao.recurso()).isEqualTo("realm eickrono user_entity");
                    assertThat(acao.quantidade()).isEqualTo(1L);
                })
                .anySatisfy(acao -> {
                    assertThat(acao.sistema()).isEqualTo("STORAGE_AVATAR");
                    assertThat(acao.tipo()).isEqualTo("MATERIALIZAR_PENDENCIA");
                    assertThat(acao.quantidade()).isEqualTo(2L);
                });
        assertThat(resposta.preservados())
                .anySatisfy(item -> {
                    assertThat(item.recurso()).isEqualTo("identidade.pessoas");
                    assertThat(item.quantidade()).isEqualTo(2L);
                })
                .anySatisfy(item -> {
                    assertThat(item.recurso()).isEqualTo("identidade.contatos_email");
                    assertThat(item.quantidade()).isEqualTo(2L);
                })
                .anySatisfy(item -> assertThat(item.recurso()).isEqualTo("clients e identity providers globais"))
                .anySatisfy(item -> {
                    assertThat(item.sistema()).isEqualTo("STORAGE_AVATAR");
                    assertThat(item.tipo()).isEqualTo("NAO_APAGAR_URL_EXTERNA");
                    assertThat(item.quantidade()).isEqualTo(2L);
                });
        assertThat(resposta.bloqueios())
                .anySatisfy(bloqueio -> assertThat(bloqueio.codigo()).isEqualTo("resolvedor_nao_implementado"));
    }

    @Test
    void deveResolverVinculoSemCompararPerfilProdutoQuandoPerfilNaoFoiInformado() {
        UUID usuarioId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        List<Map<String, Object>> produtoResolvido = List.of(Map.of("id", 1L, "codigo", "THIMISU"));
        List<Map<String, Object>> vinculoResolvido =
                List.of(Map.of("usuario_id", usuarioId, "vinculo_id", vinculoId));
        List<Map<String, Object>> usuarioKeycloakResolvido =
                List.of(Map.of("id", usuarioId, "sub_remoto", "sub-keycloak-1"));
        List<Map<String, Object>> pessoaResolvida = List.of(Map.of("pessoa_id", pessoaId));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        produtoResolvido,
                        vinculoResolvido,
                        usuarioKeycloakResolvido,
                        pessoaResolvida
                );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 1L);

        service.simular(new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "teste",
                null
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).queryForList(sqlCaptor.capture(), any(MapSqlParameterSource.class));

        String sqlVinculo = sqlCaptor.getAllValues().get(1);
        assertThat(sqlVinculo).doesNotContain(":vinculoId IS NULL");
        assertThat(sqlVinculo).doesNotContain("vinculo.id = :vinculoId");
        assertThat(sqlVinculo).contains("LOWER(vinculo.identificador_publico_cliente) = :usuarioPublicoProduto");
    }

    @Test
    void deveAdicionarPlanoDoProdutoQuandoResolvedorSuportarProduto() {
        UUID usuarioId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        List<Map<String, Object>> produtoResolvido = List.of(Map.of("id", 1L, "codigo", "THIMISU"));
        List<Map<String, Object>> vinculoResolvido =
                List.of(Map.of("usuario_id", usuarioId, "vinculo_id", vinculoId));
        List<Map<String, Object>> usuarioKeycloakResolvido =
                List.of(Map.of("id", usuarioId, "sub_remoto", "sub-keycloak-1"));
        List<Map<String, Object>> pessoaResolvida = List.of(Map.of("pessoa_id", pessoaId));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        produtoResolvido,
                        vinculoResolvido,
                        usuarioKeycloakResolvido,
                        pessoaResolvida
                );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 1L);
        ResolvedorExclusaoCadastroProdutoService resolvedor = mock(ResolvedorExclusaoCadastroProdutoService.class);
        when(resolvedor.suporta("THIMISU")).thenReturn(true);
        when(resolvedor.simular("pedrosotc", null)).thenReturn(new ResolvedorExclusaoCadastroProdutoService.Resultado(
                List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_THIMISU_BACKEND",
                        "APAGAR",
                        "perfis_sistema",
                        1L
                )),
                List.of(),
                List.of()
        ));
        service = new ExclusaoCadastroProdutoDryRunService(jdbcTemplate, List.of(resolvedor), new ObjectMapper());

        var resposta = service.simular(new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "teste",
                null
        ));

        assertThat(resposta.acoes())
                .anySatisfy(acao -> assertThat(acao.recurso()).isEqualTo("perfis_sistema"));
        assertThat(resposta.bloqueios())
                .noneSatisfy(bloqueio -> assertThat(bloqueio.sistema()).isEqualTo("EICKRONO_PRODUTO"));
    }

    @Test
    void deveBloquearExecucaoQuandoUsuarioNaoPossuirSubRemotoParaKeycloak() {
        UUID usuarioId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        List<Map<String, Object>> produtoResolvido = List.of(Map.of("id", 1L, "codigo", "THIMISU"));
        List<Map<String, Object>> vinculoResolvido =
                List.of(Map.of("usuario_id", usuarioId, "vinculo_id", vinculoId));
        List<Map<String, Object>> usuarioSemSubKeycloak =
                List.of(Map.of("id", usuarioId, "sub_remoto", ""));
        List<Map<String, Object>> pessoaResolvida = List.of(Map.of("pessoa_id", pessoaId));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        produtoResolvido,
                        vinculoResolvido,
                        usuarioSemSubKeycloak,
                        pessoaResolvida
                );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 1L);

        var resposta = service.simular(new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "teste",
                null
        ));

        assertThat(resposta.acoes())
                .anySatisfy(acao -> {
                    assertThat(acao.sistema()).isEqualTo("KEYCLOAK");
                    assertThat(acao.quantidade()).isZero();
                });
        assertThat(resposta.bloqueios())
                .anySatisfy(bloqueio -> assertThat(bloqueio.codigo()).isEqualTo("keycloak_sub_nao_resolvido"));
    }

    @Test
    void devePreservarApagamentoCentralQuandoUsuarioTiverOutroProdutoAtivo() {
        UUID usuarioId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        List<Map<String, Object>> produtoResolvido = List.of(Map.of("id", 1L, "codigo", "THIMISU"));
        List<Map<String, Object>> vinculoResolvido =
                List.of(Map.of("usuario_id", usuarioId, "vinculo_id", vinculoId));
        List<Map<String, Object>> usuarioKeycloakResolvido =
                List.of(Map.of("id", usuarioId, "sub_remoto", "sub-keycloak-1"));
        List<Map<String, Object>> pessoaResolvida = List.of(Map.of("pessoa_id", pessoaId));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        produtoResolvido,
                        vinculoResolvido,
                        usuarioKeycloakResolvido,
                        pessoaResolvida
                );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 0L, 1L, 1L, 1L);

        var resposta = service.simular(new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "teste",
                null
        ));

        assertThat(resposta.acoes())
                .anySatisfy(acao -> assertThat(acao.recurso()).isEqualTo("autenticacao.usuarios_clientes_ecossistema"))
                .noneSatisfy(acao -> assertThat(acao.recurso()).isEqualTo("autenticacao.usuarios"))
                .noneSatisfy(acao -> assertThat(acao.sistema()).isEqualTo("KEYCLOAK"));
        assertThat(resposta.preservados())
                .anySatisfy(item -> {
                    assertThat(item.sistema()).isEqualTo("EICKRONO_AUTENTICACAO_SERVIDOR");
                    assertThat(item.recurso()).isEqualTo("autenticacao.usuarios");
                })
                .anySatisfy(item -> {
                    assertThat(item.sistema()).isEqualTo("KEYCLOAK");
                    assertThat(item.recurso()).isEqualTo("realm eickrono user_entity");
                    assertThat(item.tipo()).isEqualTo("PRESERVAR");
                });
        assertThat(resposta.bloqueios())
                .noneSatisfy(bloqueio -> assertThat(bloqueio.codigo()).isEqualTo("usuario_central_compartilhado"));
    }
}
