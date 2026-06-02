package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.apresentacao.dto.admin.AlvosExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExclusaoCadastroProdutoExecucaoServiceTest {

    private static final String CORRELACAO_ID = UUID.randomUUID().toString();

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ExclusaoCadastroProdutoDryRunService dryRunService;

    @Mock
    private ResolvedorExclusaoCadastroProdutoService resolvedorProduto;

    @Mock
    private MaterializadorPendenciaRemocaoAvatarService materializadorPendenciaRemocaoAvatarService;

    @Mock
    private ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExclusaoCadastroProdutoExecucaoService service;

    @BeforeEach
    void setUp() {
        service = new ExclusaoCadastroProdutoExecucaoService(
                jdbcTemplate,
                dryRunService,
                List.of(resolvedorProduto),
                materializadorPendenciaRemocaoAvatarService,
                clienteAdministracaoCadastroKeycloak,
                objectMapper
        );
    }

    @Test
    void deveRecusarChamadaDeExecucaoComDryRunVerdadeiro() {
        ExclusaoCadastroProdutoApiRequest request = request(true);

        assertThatThrownBy(() -> service.executar(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dryRun=false");
    }

    @Test
    void deveBloquearExecucaoQuandoPlanoTiverBloqueio() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta planoAprovado = plano(List.of(), List.of());
        ExclusaoCadastroProdutoApiResposta planoAtual = plano(
                List.of(),
                List.of(new BloqueioExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_PRODUTO",
                        "alvo_invalido",
                        "Alvo invalido."
                ))
        );
        mockDryRunAprovado(request, planoAprovado);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(planoAtual);

        assertThatThrownBy(() -> service.executar(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("diverge do dryRun aprovado");
        verify(resolvedorProduto, never()).executar(any(), any(), any());
    }

    @Test
    void deveRecusarExecucaoSemCorrelacaoDoDryRunAprovado() {
        ExclusaoCadastroProdutoApiRequest request = new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                false,
                "QA",
                null
        );

        assertThatThrownBy(() -> service.executar(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("correlacaoId");
    }

    @Test
    void deveMaterializarPendenciaQuandoPlanoExigirRemocaoFisicaDeAvatar() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta plano = plano(
                List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                        "STORAGE_AVATAR",
                        "MATERIALIZAR_PENDENCIA",
                        "identidade.avatar_usuario.storage_key",
                        1L
                )),
                List.of()
        );
        mockDryRunAprovado(request, plano);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(plano);
        when(materializadorPendenciaRemocaoAvatarService.materializar(
                plano.correlacaoId(),
                "THIMISU",
                plano.alvosResolvidos().vinculosProdutoIds()
        )).thenReturn(new MaterializadorPendenciaRemocaoAvatarService.Resultado(
                List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_IDENTIDADE_SERVIDOR",
                        "MATERIALIZAR_PENDENCIA",
                        "identidade.pendencias_remocao_avatar_usuario",
                        1L
                )),
                List.of()
        ));
        when(resolvedorProduto.suporta("THIMISU")).thenReturn(true);
        when(resolvedorProduto.executar("pedrosotc", null, plano.correlacaoId()))
                .thenReturn(new ResolvedorExclusaoCadastroProdutoService.ResultadoExecucao(List.of(), List.of()));
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);

        service.executar(request);

        verify(materializadorPendenciaRemocaoAvatarService).materializar(
                plano.correlacaoId(),
                "THIMISU",
                plano.alvosResolvidos().vinculosProdutoIds()
        );
        verify(resolvedorProduto).executar("pedrosotc", null, plano.correlacaoId());
    }

    @Test
    void deveMarcarFalhaQuandoNaoMaterializarPendenciaDeAvatar() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta plano = plano(
                List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                        "STORAGE_AVATAR",
                        "MATERIALIZAR_PENDENCIA",
                        "identidade.avatar_usuario.storage_key",
                        1L
                )),
                List.of()
        );
        mockDryRunAprovado(request, plano);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(plano);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        when(materializadorPendenciaRemocaoAvatarService.materializar(
                plano.correlacaoId(),
                "THIMISU",
                plano.alvosResolvidos().vinculosProdutoIds()
        )).thenReturn(new MaterializadorPendenciaRemocaoAvatarService.Resultado(
                List.of(),
                List.of(new BloqueioExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_IDENTIDADE_SERVIDOR",
                        "avatar_pendencia_indisponivel",
                        "Identidade indisponivel."
                ))
        ));

        assertThatThrownBy(() -> service.executar(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Identidade indisponivel");
        verify(resolvedorProduto, never()).executar(any(), any(), any());
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET status = 'FALHOU'"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    void deveRemoverUsuarioCentralQuandoPlanoExigirExclusaoCompleta() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta plano = plano(
                List.of(
                        new ItemPlanoExclusaoCadastroProdutoApiResposta(
                                "EICKRONO_AUTENTICACAO_SERVIDOR",
                                "APAGAR",
                                "autenticacao.usuarios",
                                1L
                        ),
                        new ItemPlanoExclusaoCadastroProdutoApiResposta(
                                "KEYCLOAK",
                                "APAGAR",
                                "realm eickrono user_entity",
                                1L
                        )
                ),
                List.of()
        );
        mockDryRunAprovado(request, plano);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(plano);
        when(resolvedorProduto.suporta("THIMISU")).thenReturn(true);
        when(resolvedorProduto.executar("pedrosotc", null, plano.correlacaoId()))
                .thenReturn(new ResolvedorExclusaoCadastroProdutoService.ResultadoExecucao(List.of(), List.of()));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT sub_remoto"),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.eq(String.class)
        )).thenReturn(List.of("sub-remoto-1"));
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);

        service.executar(request);

        verify(clienteAdministracaoCadastroKeycloak).removerUsuario("sub-remoto-1");
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("UPDATE auditoria.usuarios_historico"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("DELETE FROM autenticacao.usuarios_formas_acesso"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("DELETE FROM autenticacao.usuarios\nWHERE id IN"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    void deveExecutarProdutoELimpezaLocalQuandoPlanoForSuportado() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta plano = plano(
                List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_AUTENTICACAO_SERVIDOR",
                        "APAGAR",
                        "autenticacao.usuarios_clientes_ecossistema",
                        1L
                )),
                List.of()
        );
        mockDryRunAprovado(request, plano);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(plano);
        when(resolvedorProduto.suporta("THIMISU")).thenReturn(true);
        when(resolvedorProduto.executar("pedrosotc", null, plano.correlacaoId()))
                .thenReturn(new ResolvedorExclusaoCadastroProdutoService.ResultadoExecucao(
                        List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                                "EICKRONO_THIMISU_BACKEND",
                                "APAGAR",
                                "thimisu.perfis_sistema",
                                1L
                        )),
                        List.of()
                ));
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);

        ExclusaoCadastroProdutoApiResposta resposta = service.executar(request);

        assertThat(resposta).isEqualTo(plano);
        verify(resolvedorProduto).executar("pedrosotc", null, plano.correlacaoId());
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET dry_run = FALSE"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("UPDATE auditoria.usuarios_clientes_ecossistema_historico"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("DELETE FROM identidade.avatar_usuario"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("DELETE FROM autenticacao.usuarios_clientes_ecossistema"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    void deveFalharQuandoPosCondicaoAindaEncontrarVinculoDoProduto() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta plano = plano(
                List.of(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_AUTENTICACAO_SERVIDOR",
                        "APAGAR",
                        "autenticacao.usuarios_clientes_ecossistema",
                        1L
                )),
                List.of()
        );
        mockDryRunAprovado(request, plano);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(plano);
        when(resolvedorProduto.suporta("THIMISU")).thenReturn(true);
        when(resolvedorProduto.executar("pedrosotc", null, plano.correlacaoId()))
                .thenReturn(new ResolvedorExclusaoCadastroProdutoService.ResultadoExecucao(List.of(), List.of()));
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.contains("FROM autenticacao.usuarios_clientes_ecossistema"),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.eq(Long.class)
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.executar(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("registros bloqueando novo cadastro");
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET status = 'FALHOU'"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    void deveMarcarFalhaQuandoProdutoNaoExecutar() {
        ExclusaoCadastroProdutoApiRequest request = request(false);
        ExclusaoCadastroProdutoApiResposta plano = plano(List.of(), List.of());
        mockDryRunAprovado(request, plano);
        when(dryRunService.planejar(request, false, CORRELACAO_ID)).thenReturn(plano);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        when(resolvedorProduto.suporta("THIMISU")).thenReturn(true);
        when(resolvedorProduto.executar("pedrosotc", null, plano.correlacaoId()))
                .thenReturn(new ResolvedorExclusaoCadastroProdutoService.ResultadoExecucao(
                        List.of(),
                        List.of(new BloqueioExclusaoCadastroProdutoApiResposta(
                                "EICKRONO_THIMISU_BACKEND",
                                "falha_produto",
                                "Produto falhou."
                        ))
                ));

        assertThatThrownBy(() -> service.executar(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Produto falhou");
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET status = 'FALHOU'"),
                any(MapSqlParameterSource.class)
        );
    }

    private static ExclusaoCadastroProdutoApiRequest request(final boolean dryRun) {
        return new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                dryRun,
                "QA",
                dryRun ? null : CORRELACAO_ID
        );
    }

    private static ExclusaoCadastroProdutoApiResposta plano(
            final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
            final List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios) {
        return new ExclusaoCadastroProdutoApiResposta(
                CORRELACAO_ID,
                false,
                new AlvosExclusaoCadastroProdutoApiResposta(
                        "THIMISU",
                        "pedrosotc",
                        null,
                        List.of(UUID.randomUUID().toString()),
                        List.of(UUID.randomUUID().toString())
                ),
                acoes,
                List.of(),
                bloqueios
        );
    }

    private void mockDryRunAprovado(final ExclusaoCadastroProdutoApiRequest request,
                                    final ExclusaoCadastroProdutoApiResposta plano) {
        try {
            when(jdbcTemplate.queryForList(
                    org.mockito.ArgumentMatchers.contains("FROM auditoria.exclusoes_cadastro_produto"),
                    any(MapSqlParameterSource.class)
            )).thenReturn(List.of(Map.of(
                    "produto", request.produto(),
                    "usuario_publico_produto", request.usuarioPublicoProduto(),
                    "perfil_produto_id", "",
                    "dry_run", true,
                    "status", "PLANEJADA",
                    "plano_json", objectMapper.writeValueAsString(plano)
            )));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
