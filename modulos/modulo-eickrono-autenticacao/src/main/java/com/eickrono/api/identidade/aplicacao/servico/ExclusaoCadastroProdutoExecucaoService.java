package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExclusaoCadastroProdutoExecucaoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusaoCadastroProdutoExecucaoService.class);

    private static final String SISTEMA_AUTENTICACAO = "EICKRONO_AUTENTICACAO_SERVIDOR";
    private static final String SISTEMA_STORAGE = "STORAGE_AVATAR";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ExclusaoCadastroProdutoDryRunService dryRunService;
    private final List<ResolvedorExclusaoCadastroProdutoService> resolvedoresProduto;
    private final MaterializadorPendenciaRemocaoAvatarService materializadorPendenciaRemocaoAvatarService;
    private final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;
    private final ObjectMapper objectMapper;

    public ExclusaoCadastroProdutoExecucaoService(
            final NamedParameterJdbcTemplate jdbcTemplate,
            final ExclusaoCadastroProdutoDryRunService dryRunService,
            final List<ResolvedorExclusaoCadastroProdutoService> resolvedoresProduto,
            final MaterializadorPendenciaRemocaoAvatarService materializadorPendenciaRemocaoAvatarService,
            final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
            final ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate e obrigatorio");
        this.dryRunService = Objects.requireNonNull(dryRunService, "dryRunService e obrigatorio");
        this.resolvedoresProduto = List.copyOf(
                Objects.requireNonNull(resolvedoresProduto, "resolvedoresProduto e obrigatorio")
        );
        this.materializadorPendenciaRemocaoAvatarService = Objects.requireNonNull(
                materializadorPendenciaRemocaoAvatarService,
                "materializadorPendenciaRemocaoAvatarService e obrigatorio"
        );
        this.clienteAdministracaoCadastroKeycloak = Objects.requireNonNull(
                clienteAdministracaoCadastroKeycloak,
                "clienteAdministracaoCadastroKeycloak e obrigatorio"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper e obrigatorio");
    }

    @Transactional
    public ExclusaoCadastroProdutoApiResposta executar(final ExclusaoCadastroProdutoApiRequest requisicao) {
        Objects.requireNonNull(requisicao, "requisicao e obrigatoria");
        if (requisicao.dryRun()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use o dryRun para simulacao; execucao real exige dryRun=false."
            );
        }

        DryRunAprovado dryRunAprovado = carregarDryRunAprovado(requisicao);
        LOGGER.info(
                "exclusao_cadastro_produto_execucao_iniciada correlacaoId={} produto={} usuarioPublicoProduto={} perfilProdutoIdPresente={}",
                dryRunAprovado.correlacaoId(),
                requisicao.produto(),
                requisicao.usuarioPublicoProduto(),
                requisicao.perfilProdutoId() != null && !requisicao.perfilProdutoId().isBlank()
        );
        try {
            ExclusaoCadastroProdutoApiResposta plano = dryRunService.planejar(
                    requisicao,
                    false,
                    dryRunAprovado.correlacaoId()
            );
            validarPlanoAprovado(dryRunAprovado.plano(), plano);
            iniciarExecucaoAprovada(dryRunAprovado.correlacaoId());

            MaterializadorPendenciaRemocaoAvatarService.Resultado resultadoAvatar = materializarPendenciasAvatar(plano);
            if (!resultadoAvatar.bloqueios().isEmpty()) {
                LOGGER.warn(
                        "exclusao_cadastro_produto_execucao_bloqueada_avatar correlacaoId={} codigo={} detalhe={}",
                        plano.correlacaoId(),
                        resultadoAvatar.bloqueios().get(0).codigo(),
                        resultadoAvatar.bloqueios().get(0).detalhe()
                );
                marcarFalha(plano, resultadoAvatar.bloqueios().get(0));
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        resultadoAvatar.bloqueios().get(0).detalhe()
                );
            }

            ResolvedorExclusaoCadastroProdutoService resolvedorProduto = resolvedorProduto(plano.alvosResolvidos().produto());
            ResolvedorExclusaoCadastroProdutoService.ResultadoExecucao resultadoProduto =
                    resolvedorProduto.executar(
                            plano.alvosResolvidos().usuarioPublicoProduto(),
                            plano.alvosResolvidos().perfilProdutoId(),
                            plano.correlacaoId()
                    );
            if (!resultadoProduto.bloqueios().isEmpty()) {
                LOGGER.warn(
                        "exclusao_cadastro_produto_execucao_bloqueada_produto correlacaoId={} codigo={} detalhe={}",
                        plano.correlacaoId(),
                        resultadoProduto.bloqueios().get(0).codigo(),
                        resultadoProduto.bloqueios().get(0).detalhe()
                );
                marcarFalha(plano, resultadoProduto.bloqueios().get(0));
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        resultadoProduto.bloqueios().get(0).detalhe()
                );
            }

            removerUsuariosKeycloakQuandoNecessario(plano);
            executarLimpezaAutenticacao(plano);
            validarPosCondicoesAutenticacao(plano);
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoesExecutadas = new ArrayList<>();
            acoesExecutadas.addAll(resultadoAvatar.acoesExecutadas());
            acoesExecutadas.addAll(resultadoProduto.acoesExecutadas());
            marcarConcluida(plano, acoesExecutadas);
            LOGGER.info(
                    "exclusao_cadastro_produto_execucao_concluida correlacaoId={} acoesExecutadas={} usuarios={} vinculos={}",
                    plano.correlacaoId(),
                    acoesExecutadas.size(),
                    plano.alvosResolvidos().usuariosAutenticacaoIds().size(),
                    plano.alvosResolvidos().vinculosProdutoIds().size()
            );
            return plano;
        } catch (RuntimeException ex) {
            LOGGER.error(
                    "exclusao_cadastro_produto_execucao_falhou correlacaoId={} produto={} erro={}",
                    dryRunAprovado.correlacaoId(),
                    requisicao.produto(),
                    ex.getClass().getSimpleName(),
                    ex
            );
            throw ex;
        }
    }

    private DryRunAprovado carregarDryRunAprovado(final ExclusaoCadastroProdutoApiRequest requisicao) {
        UUID correlacaoId = correlacaoIdObrigatorio(requisicao);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("correlacaoId", correlacaoId, Types.OTHER);
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
                SELECT produto,
                       usuario_publico_produto,
                       perfil_produto_id,
                       dry_run,
                       status,
                       plano_json
                FROM auditoria.exclusoes_cadastro_produto
                WHERE correlacao_id = :correlacaoId
                LIMIT 1
                """, params);
        if (linhas.isEmpty()) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_dryrun_aprovado_nao_encontrado correlacaoId={}",
                    correlacaoId
            );
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Execucao real exige correlacaoId de um dryRun previamente registrado."
            );
        }

        Map<String, Object> linha = linhas.get(0);
        if (!Boolean.TRUE.equals(linha.get("dry_run")) || !"PLANEJADA".equals(Objects.toString(linha.get("status")))) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_dryrun_status_invalido correlacaoId={} dryRun={} status={}",
                    correlacaoId,
                    linha.get("dry_run"),
                    linha.get("status")
            );
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "O dryRun informado nao esta planejado para execucao."
            );
        }
        validarMesmoAlvo(requisicao, linha);
        LOGGER.info(
                "exclusao_cadastro_produto_dryrun_aprovado_carregado correlacaoId={} produto={} usuarioPublicoProduto={}",
                correlacaoId,
                linha.get("produto"),
                linha.get("usuario_publico_produto")
        );
        return new DryRunAprovado(correlacaoId.toString(), lerPlanoJson(linha.get("plano_json")));
    }

    private UUID correlacaoIdObrigatorio(final ExclusaoCadastroProdutoApiRequest requisicao) {
        if (requisicao.correlacaoId() == null || requisicao.correlacaoId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "correlacaoId do dryRun aprovado e obrigatorio para dryRun=false."
            );
        }
        try {
            return UUID.fromString(requisicao.correlacaoId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correlacaoId invalido.", ex);
        }
    }

    private void validarMesmoAlvo(final ExclusaoCadastroProdutoApiRequest requisicao,
                                  final Map<String, Object> linha) {
        if (!Objects.equals(normalizar(requisicao.produto()), normalizar(Objects.toString(linha.get("produto"), null)))
                || !Objects.equals(
                        normalizar(requisicao.usuarioPublicoProduto()),
                        normalizar(Objects.toString(linha.get("usuario_publico_produto"), null))
                )
                || !Objects.equals(
                        normalizar(requisicao.perfilProdutoId()),
                        normalizar(Objects.toString(linha.get("perfil_produto_id"), null))
                )) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "O alvo da execucao difere do dryRun aprovado."
            );
        }
    }

    private String normalizar(final String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toLowerCase(Locale.ROOT);
    }

    private ExclusaoCadastroProdutoApiResposta lerPlanoJson(final Object planoJson) {
        try {
            return objectMapper.readValue(Objects.toString(planoJson), ExclusaoCadastroProdutoApiResposta.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "O plano do dryRun aprovado nao pode ser lido.",
                    ex
            );
        }
    }

    private void validarPlanoAprovado(final ExclusaoCadastroProdutoApiResposta aprovado,
                                      final ExclusaoCadastroProdutoApiResposta atual) {
        if (!aprovado.bloqueios().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "O dryRun aprovado possui bloqueios e nao pode ser executado."
            );
        }
        if (!atual.bloqueios().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O plano atual diverge do dryRun aprovado; execute novo dryRun."
            );
        }
        if (!Objects.equals(aprovado.alvosResolvidos(), atual.alvosResolvidos())
                || !Objects.equals(aprovado.acoes(), atual.acoes())
                || !Objects.equals(aprovado.preservados(), atual.preservados())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O plano atual diverge do dryRun aprovado; execute novo dryRun."
            );
        }
    }

    private void iniciarExecucaoAprovada(final String correlacaoId) {
        int atualizados = jdbcTemplate.update("""
                UPDATE auditoria.exclusoes_cadastro_produto
                SET dry_run = FALSE,
                    status = 'EM_EXECUCAO',
                    iniciado_em = NOW(),
                    atualizado_em = NOW()
                WHERE correlacao_id = :correlacaoId
                  AND dry_run = TRUE
                  AND status = 'PLANEJADA'
                """, new MapSqlParameterSource()
                .addValue("correlacaoId", UUID.fromString(correlacaoId), Types.OTHER));
        LOGGER.info(
                "exclusao_cadastro_produto_execucao_auditoria_iniciada correlacaoId={} linhasAtualizadas={}",
                correlacaoId,
                atualizados
        );
        if (atualizados != 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nao foi possivel iniciar a execucao do dryRun aprovado."
            );
        }
    }

    private void removerUsuariosKeycloakQuandoNecessario(final ExclusaoCadastroProdutoApiResposta plano) {
        boolean exigeKeycloak = plano.acoes().stream().anyMatch(acao ->
                "KEYCLOAK".equals(acao.sistema())
                        && "APAGAR".equals(acao.tipo())
                        && acao.quantidade() > 0L);
        if (!exigeKeycloak) {
            LOGGER.info(
                    "exclusao_cadastro_produto_keycloak_ignorado correlacaoId={} motivo=acao_nao_planejada",
                    plano.correlacaoId()
            );
            return;
        }
        try {
            List<String> subjects = resolverSubjectsKeycloak(plano);
            LOGGER.info(
                    "exclusao_cadastro_produto_keycloak_remocao_iniciada correlacaoId={} usuariosKeycloak={}",
                    plano.correlacaoId(),
                    subjects.size()
            );
            for (String subjectRemoto : subjects) {
                clienteAdministracaoCadastroKeycloak.removerUsuario(subjectRemoto);
            }
            LOGGER.info(
                    "exclusao_cadastro_produto_keycloak_remocao_concluida correlacaoId={} usuariosKeycloak={}",
                    plano.correlacaoId(),
                    subjects.size()
            );
        } catch (RuntimeException ex) {
            BloqueioExclusaoCadastroProdutoApiResposta bloqueio = new BloqueioExclusaoCadastroProdutoApiResposta(
                    "KEYCLOAK",
                    "keycloak_remocao_usuario_falhou",
                    "Nao foi possivel remover o usuario no servidor de autorizacao."
            );
            marcarFalha(plano, bloqueio);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, bloqueio.detalhe(), ex);
        }
    }

    private List<String> resolverSubjectsKeycloak(final ExclusaoCadastroProdutoApiResposta plano) {
        List<UUID> usuariosIds = plano.alvosResolvidos().usuariosAutenticacaoIds().stream()
                .map(UUID::fromString)
                .toList();
        if (usuariosIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT sub_remoto
                FROM autenticacao.usuarios
                WHERE id IN (:usuariosIds)
                  AND sub_remoto IS NOT NULL
                """, new MapSqlParameterSource().addValue("usuariosIds", usuariosIds), String.class);
    }

    private MaterializadorPendenciaRemocaoAvatarService.Resultado materializarPendenciasAvatar(
            final ExclusaoCadastroProdutoApiResposta plano) {
        boolean exigeRemocaoStorage = plano.acoes().stream().anyMatch(acao ->
                SISTEMA_STORAGE.equals(acao.sistema())
                        && "MATERIALIZAR_PENDENCIA".equals(acao.tipo())
                        && acao.quantidade() > 0L);
        if (!exigeRemocaoStorage) {
            LOGGER.info(
                    "exclusao_cadastro_produto_avatar_pendencia_ignorada correlacaoId={} motivo=sem_storage_planejado",
                    plano.correlacaoId()
            );
            return new MaterializadorPendenciaRemocaoAvatarService.Resultado(List.of(), List.of());
        }
        LOGGER.info(
                "exclusao_cadastro_produto_avatar_pendencia_iniciada correlacaoId={} produto={} vinculos={}",
                plano.correlacaoId(),
                plano.alvosResolvidos().produto(),
                plano.alvosResolvidos().vinculosProdutoIds().size()
        );
        MaterializadorPendenciaRemocaoAvatarService.Resultado resultado =
                materializadorPendenciaRemocaoAvatarService.materializar(
                plano.correlacaoId(),
                plano.alvosResolvidos().produto(),
                plano.alvosResolvidos().vinculosProdutoIds()
        );
        LOGGER.info(
                "exclusao_cadastro_produto_avatar_pendencia_concluida correlacaoId={} acoes={} bloqueios={}",
                plano.correlacaoId(),
                resultado.acoesExecutadas().size(),
                resultado.bloqueios().size()
        );
        return resultado;
    }

    private ResolvedorExclusaoCadastroProdutoService resolvedorProduto(final String produto) {
        String produtoNormalizado = produto == null ? "" : produto.trim().toUpperCase(Locale.ROOT);
        return resolvedoresProduto.stream()
                .filter(candidato -> candidato.suporta(produtoNormalizado))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Nenhum executor foi encontrado para o produto informado."
                ));
    }

    private void executarLimpezaAutenticacao(final ExclusaoCadastroProdutoApiResposta plano) {
        List<UUID> vinculosIds = plano.alvosResolvidos().vinculosProdutoIds().stream()
                .map(UUID::fromString)
                .toList();
        List<UUID> usuariosIds = plano.alvosResolvidos().usuariosAutenticacaoIds().stream()
                .map(UUID::fromString)
                .toList();
        if (vinculosIds.isEmpty() && usuariosIds.isEmpty()) {
            LOGGER.info(
                    "exclusao_cadastro_produto_autenticacao_limpeza_ignorada correlacaoId={} motivo=alvo_vazio",
                    plano.correlacaoId()
            );
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vinculosIds", vinculosIds)
                .addValue("usuariosIds", usuariosIds)
                .addValue("correlacaoId", UUID.fromString(plano.correlacaoId()), Types.OTHER);

        boolean excluirUsuarioCentral = deveExcluirUsuarioCentral(plano);
        LOGGER.info(
                "exclusao_cadastro_produto_autenticacao_limpeza_iniciada correlacaoId={} vinculos={} usuarios={} excluirUsuarioCentral={}",
                plano.correlacaoId(),
                vinculosIds.size(),
                usuariosIds.size(),
                excluirUsuarioCentral
        );
        if (excluirUsuarioCentral) {
            anonimizarReferenciasOperacionaisUsuarioCentral(params);
            removerDependenciasUsuarioCentral(params);
        }
        if (!vinculosIds.isEmpty()) {
            jdbcTemplate.update("""
                UPDATE auditoria.usuarios_clientes_ecossistema_historico
                SET vinculo_id = NULL,
                    identificador_publico_cliente = 'anonimizado:' || id::TEXT,
                    origem_alteracao = 'EXCLUSAO_CADASTRO_PRODUTO',
                    anonimizado_em = NOW(),
                    correlacao_exclusao_cadastro_produto = :correlacaoId
                WHERE vinculo_id IN (:vinculosIds)
                  AND anonimizado_em IS NULL
                """, params);
            jdbcTemplate.update("""
                DELETE FROM identidade.avatar_usuario
                WHERE usuario_cliente_id IN (:vinculosIds)
                """, params);
            jdbcTemplate.update("""
                DELETE FROM autenticacao.usuarios_clientes_ecossistema
                WHERE id IN (:vinculosIds)
                """, params);
        }
        if (excluirUsuarioCentral) {
            removerUsuarioCentral(params);
        }
        LOGGER.info(
                "exclusao_cadastro_produto_autenticacao_limpeza_concluida correlacaoId={}",
                plano.correlacaoId()
        );
    }

    private void validarPosCondicoesAutenticacao(final ExclusaoCadastroProdutoApiResposta plano) {
        List<UUID> vinculosIds = plano.alvosResolvidos().vinculosProdutoIds().stream()
                .map(UUID::fromString)
                .toList();
        List<UUID> usuariosIds = plano.alvosResolvidos().usuariosAutenticacaoIds().stream()
                .map(UUID::fromString)
                .toList();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vinculosIds", vinculosIds)
                .addValue("usuariosIds", usuariosIds);

        List<String> pendencias = new ArrayList<>();
        if (!vinculosIds.isEmpty()) {
            adicionarPendenciaSeRestar(
                    pendencias,
                    "autenticacao.usuarios_clientes_ecossistema",
                    """
                    SELECT COUNT(*)
                    FROM autenticacao.usuarios_clientes_ecossistema
                    WHERE id IN (:vinculosIds)
                    """,
                    params
            );
            adicionarPendenciaSeRestar(
                    pendencias,
                    "identidade.avatar_usuario",
                    """
                    SELECT COUNT(*)
                    FROM identidade.avatar_usuario
                    WHERE usuario_cliente_id IN (:vinculosIds)
                    """,
                    params
            );
        }
        if (deveExcluirUsuarioCentral(plano) && !usuariosIds.isEmpty()) {
            adicionarPendenciaSeRestar(
                    pendencias,
                    "autenticacao.usuarios",
                    """
                    SELECT COUNT(*)
                    FROM autenticacao.usuarios
                    WHERE id IN (:usuariosIds)
                    """,
                    params
            );
            adicionarPendenciaSeRestar(
                    pendencias,
                    "autenticacao.usuarios_formas_acesso",
                    """
                    SELECT COUNT(*)
                    FROM autenticacao.usuarios_formas_acesso
                    WHERE usuario_id IN (:usuariosIds)
                    """,
                    params
            );
            adicionarPendenciaSeRestar(
                    pendencias,
                    "autenticacao.cadastros_conta",
                    """
                    SELECT COUNT(*)
                    FROM autenticacao.cadastros_conta
                    WHERE usuario_id IN (:usuariosIds)
                    """,
                    params
            );
        }
        if (!pendencias.isEmpty()) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_pos_condicao_falhou correlacaoId={} pendencias={}",
                    plano.correlacaoId(),
                    String.join(",", pendencias)
            );
            BloqueioExclusaoCadastroProdutoApiResposta bloqueio = new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_AUTENTICACAO,
                    "pos_condicao_falhou",
                    "A exclusao foi executada, mas ainda existem registros bloqueando novo cadastro: "
                            + String.join(", ", pendencias) + "."
            );
            marcarFalha(plano, bloqueio);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, bloqueio.detalhe());
        }
    }

    private void adicionarPendenciaSeRestar(final List<String> pendencias,
                                            final String recurso,
                                            final String sql,
                                            final MapSqlParameterSource params) {
        Long quantidade = jdbcTemplate.queryForObject(sql, params, Long.class);
        if (quantidade != null && quantidade > 0L) {
            pendencias.add(recurso + "=" + quantidade);
        }
    }

    private boolean deveExcluirUsuarioCentral(final ExclusaoCadastroProdutoApiResposta plano) {
        return plano.acoes().stream().anyMatch(acao ->
                SISTEMA_AUTENTICACAO.equals(acao.sistema())
                        && "APAGAR".equals(acao.tipo())
                        && "autenticacao.usuarios".equals(acao.recurso())
                        && acao.quantidade() > 0L);
    }

    private void anonimizarReferenciasOperacionaisUsuarioCentral(final MapSqlParameterSource params) {
        jdbcTemplate.update("""
                UPDATE auditoria.usuarios_historico
                SET usuario_id = NULL,
                    sub_remoto = NULL,
                    origem_alteracao = 'EXCLUSAO_CADASTRO_PRODUTO',
                    anonimizado_em = NOW(),
                    correlacao_exclusao_cadastro_produto = :correlacaoId
                WHERE usuario_id IN (:usuariosIds)
                  AND anonimizado_em IS NULL
                """, params);
        jdbcTemplate.update("""
                UPDATE seguranca.atestacoes_app_desafios
                SET usuario_id = NULL,
                    vinculo_cliente_id = NULL,
                    cadastro_id = NULL,
                    registro_dispositivo_id = NULL,
                    dispositivo_id = NULL
                WHERE usuario_id IN (:usuariosIds)
                   OR vinculo_cliente_id IN (:vinculosIds)
                   OR cadastro_id IN (
                        SELECT id FROM autenticacao.cadastros_conta WHERE usuario_id IN (:usuariosIds)
                   )
                   OR registro_dispositivo_id IN (
                        SELECT id FROM dispositivos.registros_dispositivo WHERE usuario_id IN (:usuariosIds)
                   )
                   OR dispositivo_id IN (
                        SELECT id FROM dispositivos.dispositivos_confiaveis WHERE usuario_id IN (:usuariosIds)
                   )
                """, params);
        jdbcTemplate.update("""
                UPDATE auditoria.operacoes_atestadas
                SET usuario_id = NULL,
                    vinculo_cliente_id = NULL,
                    cadastro_id = NULL,
                    registro_dispositivo_id = NULL,
                    dispositivo_id = NULL,
                    token_dispositivo_id = NULL,
                    credencial_atestacao_id = NULL,
                    identificador_principal = NULL
                WHERE usuario_id IN (:usuariosIds)
                   OR vinculo_cliente_id IN (:vinculosIds)
                   OR cadastro_id IN (
                        SELECT id FROM autenticacao.cadastros_conta WHERE usuario_id IN (:usuariosIds)
                   )
                   OR registro_dispositivo_id IN (
                        SELECT id FROM dispositivos.registros_dispositivo WHERE usuario_id IN (:usuariosIds)
                   )
                   OR dispositivo_id IN (
                        SELECT id FROM dispositivos.dispositivos_confiaveis WHERE usuario_id IN (:usuariosIds)
                   )
                   OR token_dispositivo_id IN (
                        SELECT token.id
                        FROM dispositivos.tokens_dispositivo token
                        JOIN dispositivos.dispositivos_confiaveis dispositivo
                          ON dispositivo.id = token.dispositivo_id
                        WHERE dispositivo.usuario_id IN (:usuariosIds)
                   )
                   OR credencial_atestacao_id IN (
                        SELECT id
                        FROM seguranca.credenciais_atestacao_dispositivo
                        WHERE usuario_id IN (:usuariosIds)
                   )
                """, params);
    }

    private void removerDependenciasUsuarioCentral(final MapSqlParameterSource params) {
        jdbcTemplate.update("""
                DELETE FROM dispositivos.tokens_dispositivo
                WHERE dispositivo_id IN (
                        SELECT id FROM dispositivos.dispositivos_confiaveis WHERE usuario_id IN (:usuariosIds)
                    )
                   OR registro_dispositivo_id IN (
                        SELECT id FROM dispositivos.registros_dispositivo WHERE usuario_id IN (:usuariosIds)
                    )
                """, params);
        jdbcTemplate.update("""
                DELETE FROM seguranca.credenciais_atestacao_dispositivo
                WHERE usuario_id IN (:usuariosIds)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM dispositivos.dispositivos_confiaveis
                WHERE usuario_id IN (:usuariosIds)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM dispositivos.registros_dispositivo
                WHERE usuario_id IN (:usuariosIds)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM autenticacao.recuperacoes_senha
                WHERE usuario_id IN (:usuariosIds)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM autenticacao.cadastros_conta
                WHERE usuario_id IN (:usuariosIds)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM autenticacao.usuarios_formas_acesso
                WHERE usuario_id IN (:usuariosIds)
                """, params);
    }

    private void removerUsuarioCentral(final MapSqlParameterSource params) {
        jdbcTemplate.update("""
                DELETE FROM autenticacao.usuarios
                WHERE id IN (:usuariosIds)
                """, params);
    }

    private void marcarConcluida(final ExclusaoCadastroProdutoApiResposta plano,
                                 final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoesProdutoExecutadas) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("correlacaoId", UUID.fromString(plano.correlacaoId()), Types.OTHER)
                .addValue("resultado", acoesProdutoExecutadas.toString(), Types.VARCHAR);
        jdbcTemplate.update("""
                UPDATE auditoria.exclusoes_cadastro_produto
                SET status = 'CONCLUIDA',
                    concluido_em = NOW(),
                    atualizado_em = NOW(),
                    resultado_json = jsonb_build_object('acoesProdutoExecutadas', :resultado)
                WHERE correlacao_id = :correlacaoId
                """, params);
        jdbcTemplate.update("""
                UPDATE auditoria.exclusoes_cadastro_produto_etapas etapa
                SET status = CASE WHEN etapa.status = 'PLANEJADA' THEN 'CONCLUIDA' ELSE etapa.status END,
                    concluido_em = CASE WHEN etapa.status = 'PLANEJADA' THEN NOW() ELSE etapa.concluido_em END,
                    atualizado_em = NOW()
                FROM auditoria.exclusoes_cadastro_produto exclusao
                WHERE etapa.exclusao_id = exclusao.id
                  AND exclusao.correlacao_id = :correlacaoId
                """, params);
        LOGGER.info(
                "exclusao_cadastro_produto_auditoria_concluida correlacaoId={} acoesProdutoExecutadas={}",
                plano.correlacaoId(),
                acoesProdutoExecutadas.size()
        );
    }

    private void marcarFalha(final ExclusaoCadastroProdutoApiResposta plano,
                             final BloqueioExclusaoCadastroProdutoApiResposta bloqueio) {
        jdbcTemplate.update("""
                UPDATE auditoria.exclusoes_cadastro_produto
                SET status = 'FALHOU',
                    atualizado_em = NOW(),
                    resultado_json = jsonb_build_object(
                        'erroCodigo', :erroCodigo,
                        'erroMensagem', :erroMensagem
                    )
                WHERE correlacao_id = :correlacaoId
                """, new MapSqlParameterSource()
                .addValue("correlacaoId", UUID.fromString(plano.correlacaoId()), Types.OTHER)
                .addValue("erroCodigo", bloqueio.codigo())
                .addValue("erroMensagem", bloqueio.detalhe()));
        LOGGER.warn(
                "exclusao_cadastro_produto_auditoria_falha_registrada correlacaoId={} sistema={} codigo={} detalhe={}",
                plano.correlacaoId(),
                bloqueio.sistema(),
                bloqueio.codigo(),
                bloqueio.detalhe()
        );
    }

    private record DryRunAprovado(String correlacaoId, ExclusaoCadastroProdutoApiResposta plano) {
    }
}
