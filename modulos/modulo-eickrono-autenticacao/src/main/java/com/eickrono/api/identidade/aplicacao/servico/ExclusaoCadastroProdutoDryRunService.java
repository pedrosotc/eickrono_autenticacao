package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.apresentacao.dto.admin.AlvosExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ExclusaoCadastroProdutoDryRunService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusaoCadastroProdutoDryRunService.class);

    private static final String SISTEMA_AUTENTICACAO = "EICKRONO_AUTENTICACAO_SERVIDOR";
    private static final String SISTEMA_IDENTIDADE = "EICKRONO_IDENTIDADE_SERVIDOR";
    private static final String SISTEMA_PRODUTO = "EICKRONO_PRODUTO";
    private static final String SISTEMA_KEYCLOAK = "KEYCLOAK";
    private static final String SISTEMA_STORAGE = "STORAGE_AVATAR";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final List<ResolvedorExclusaoCadastroProdutoService> resolvedoresProduto;
    private final ObjectMapper objectMapper;

    public ExclusaoCadastroProdutoDryRunService(
            final NamedParameterJdbcTemplate jdbcTemplate,
            final List<ResolvedorExclusaoCadastroProdutoService> resolvedoresProduto,
            final ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate e obrigatorio");
        this.resolvedoresProduto = List.copyOf(
                Objects.requireNonNull(resolvedoresProduto, "resolvedoresProduto e obrigatorio")
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper e obrigatorio");
    }

    @Transactional
    public ExclusaoCadastroProdutoApiResposta simular(final ExclusaoCadastroProdutoApiRequest requisicao) {
        Objects.requireNonNull(requisicao, "requisicao e obrigatoria");
        if (!requisicao.dryRun()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Use o servico de execucao para dryRun=false."
            );
        }
        ExclusaoCadastroProdutoApiResposta resposta = planejar(requisicao, true);
        registrarOperacao(requisicao, resposta);
        LOGGER.info(
                "exclusao_cadastro_produto_dryrun_registrado correlacaoId={} produto={} usuarioPublicoProduto={} usuariosResolvidos={} vinculosResolvidos={} acoes={} preservados={} bloqueios={}",
                resposta.correlacaoId(),
                resposta.alvosResolvidos().produto(),
                resposta.alvosResolvidos().usuarioPublicoProduto(),
                resposta.alvosResolvidos().usuariosAutenticacaoIds().size(),
                resposta.alvosResolvidos().vinculosProdutoIds().size(),
                resposta.acoes().size(),
                resposta.preservados().size(),
                resposta.bloqueios().size()
        );
        return resposta;
    }

    ExclusaoCadastroProdutoApiResposta planejar(final ExclusaoCadastroProdutoApiRequest requisicao,
                                                final boolean dryRun) {
        return planejar(requisicao, dryRun, UUID.randomUUID().toString());
    }

    ExclusaoCadastroProdutoApiResposta planejar(final ExclusaoCadastroProdutoApiRequest requisicao,
                                                final boolean dryRun,
                                                final String correlacaoId) {
        Objects.requireNonNull(requisicao, "requisicao e obrigatoria");
        validar(requisicao);
        LOGGER.info(
                "exclusao_cadastro_produto_planejamento_iniciado dryRun={} correlacaoId={} produto={} usuarioPublicoProduto={} perfilProdutoIdPresente={}",
                dryRun,
                correlacaoId,
                requisicao.produto(),
                requisicao.usuarioPublicoProduto(),
                requisicao.perfilProdutoId() != null && !requisicao.perfilProdutoId().isBlank()
        );

        List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes = new ArrayList<>();
        List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados = new ArrayList<>();
        List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios = new ArrayList<>();

        ProdutoResolvido produto = resolverProduto(requisicao.produto());
        if (produto == null) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_produto_nao_resolvido correlacaoId={} produto={}",
                    correlacaoId,
                    requisicao.produto()
            );
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_AUTENTICACAO,
                    "produto_nao_encontrado",
                    "Nenhum cliente de ecossistema ativo foi encontrado para o produto informado."
            ));
            ExclusaoCadastroProdutoApiResposta resposta =
                    resposta(requisicao, dryRun, correlacaoId, List.of(), List.of(), acoes, preservados, bloqueios);
            return resposta;
        }

        List<VinculoProdutoResolvido> vinculos = resolverVinculosProduto(produto, requisicao);
        List<String> usuariosIds = vinculos.stream().map(VinculoProdutoResolvido::usuarioId).distinct().toList();
        List<String> vinculosIds = vinculos.stream().map(VinculoProdutoResolvido::vinculoId).distinct().toList();
        boolean usuarioCentralExclusivoDoProduto =
                !usuariosIds.isEmpty() && contarVinculosAtivosForaDoAlvo(usuariosIds, vinculosIds) == 0L;
        LOGGER.info(
                "exclusao_cadastro_produto_alvo_resolvido correlacaoId={} produto={} produtoId={} vinculos={} usuarios={} usuarioCentralExclusivoDoProduto={}",
                correlacaoId,
                produto.codigo(),
                produto.id(),
                vinculosIds.size(),
                usuariosIds.size(),
                usuarioCentralExclusivoDoProduto
        );

        adicionarAcoesAutenticacao(usuariosIds, vinculosIds, usuarioCentralExclusivoDoProduto, acoes, preservados);
        adicionarPlanoKeycloak(usuariosIds, usuarioCentralExclusivoDoProduto, acoes, preservados, bloqueios);
        adicionarPlanoStorageAvatar(vinculosIds, acoes, preservados);
        adicionarPlanoProduto(produto.codigo(), requisicao, acoes, preservados, bloqueios);
        adicionarPreservados(usuariosIds, preservados);

        if (usuariosIds.isEmpty()) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_alvo_nao_resolvido correlacaoId={} produto={} usuarioPublicoProduto={} perfilProdutoIdPresente={}",
                    correlacaoId,
                    produto.codigo(),
                    requisicao.usuarioPublicoProduto(),
                    requisicao.perfilProdutoId() != null && !requisicao.perfilProdutoId().isBlank()
            );
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_AUTENTICACAO,
                    "alvo_nao_resolvido",
                    "Nenhum vinculo usuario/produto foi encontrado para os identificadores informados."
            ));
        }

        ExclusaoCadastroProdutoApiResposta resposta =
                resposta(requisicao, dryRun, correlacaoId, usuariosIds, vinculosIds, acoes, preservados, bloqueios);
        LOGGER.info(
                "exclusao_cadastro_produto_planejamento_concluido dryRun={} correlacaoId={} acoes={} preservados={} bloqueios={}",
                dryRun,
                correlacaoId,
                resposta.acoes().size(),
                resposta.preservados().size(),
                resposta.bloqueios().size()
        );
        return resposta;
    }

    private void validar(final ExclusaoCadastroProdutoApiRequest requisicao) {
        if (!StringUtils.hasText(requisicao.produto())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "produto e obrigatorio.");
        }
        if (!StringUtils.hasText(requisicao.usuarioPublicoProduto())
                && !StringUtils.hasText(requisicao.perfilProdutoId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "usuarioPublicoProduto ou perfilProdutoId deve ser informado."
            );
        }
    }

    private ProdutoResolvido resolverProduto(final String produto) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("produto", normalizar(produto));
        List<Map<String, Object>> resultados = jdbcTemplate.queryForList("""
                SELECT id, codigo
                FROM catalogo.clientes_ecossistema
                WHERE (
                    LOWER(codigo) = :produto
                    OR LOWER(produto_exibicao) = :produto
                    OR LOWER(nome) = :produto
                )
                  AND ativo = TRUE
                ORDER BY id
                LIMIT 1
                """, params);
        if (resultados.isEmpty()) {
            return null;
        }
        Map<String, Object> linha = resultados.get(0);
        return new ProdutoResolvido(
                ((Number) linha.get("id")).longValue(),
                Objects.toString(linha.get("codigo"), produto)
        );
    }

    private List<VinculoProdutoResolvido> resolverVinculosProduto(
            final ProdutoResolvido produto,
            final ExclusaoCadastroProdutoApiRequest requisicao) {
        String usuarioPublicoProduto = normalizar(requisicao.usuarioPublicoProduto());
        UUID vinculoId = uuidOuNulo(requisicao.perfilProdutoId());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clienteEcossistemaId", produto.id())
                .addValue("usuarioPublicoProduto", usuarioPublicoProduto, Types.VARCHAR)
                .addValue("vinculoId", vinculoId, Types.OTHER);
        StringBuilder sql = new StringBuilder("""
                SELECT vinculo.id AS vinculo_id,
                       vinculo.usuario_id AS usuario_id
                FROM autenticacao.usuarios_clientes_ecossistema vinculo
                WHERE vinculo.cliente_ecossistema_id = :clienteEcossistemaId
                  AND COALESCE(vinculo.status_vinculo, '') <> 'REVOGADO'
                """);
        if (usuarioPublicoProduto != null) {
            sql.append("""
                      AND LOWER(vinculo.identificador_publico_cliente) = :usuarioPublicoProduto
                    """);
        }
        if (vinculoId != null) {
            sql.append("""
                      AND vinculo.id = :vinculoId
                    """);
        }
        sql.append("""
                ORDER BY vinculo.vinculado_em DESC
                """);
        List<Map<String, Object>> resultados = jdbcTemplate.queryForList(sql.toString(), params);
        LOGGER.info(
                "exclusao_cadastro_produto_vinculos_consultados produto={} usuarioPublicoProduto={} perfilProdutoIdPresente={} quantidade={}",
                produto.codigo(),
                requisicao.usuarioPublicoProduto(),
                requisicao.perfilProdutoId() != null && !requisicao.perfilProdutoId().isBlank(),
                resultados.size()
        );
        return resultados.stream()
                .map(linha -> new VinculoProdutoResolvido(
                        Objects.toString(linha.get("vinculo_id")),
                        Objects.toString(linha.get("usuario_id"))
                ))
                .toList();
    }

    private void adicionarAcoesAutenticacao(final List<String> usuariosIds,
                                            final List<String> vinculosIds,
                                            final boolean usuarioCentralExclusivoDoProduto,
                                            final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
                                            final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados) {
        acoes.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                "autenticacao.usuarios_clientes_ecossistema",
                vinculosIds.size()
        ));
        acoes.add(acaoPorVinculos("identidade.avatar_usuario", vinculosIds));
        acoes.add(anonimizarPorVinculos("auditoria.usuarios_clientes_ecossistema_historico", vinculosIds));
        if (usuarioCentralExclusivoDoProduto) {
            acoes.add(acaoPorUsuarios("autenticacao.usuarios_formas_acesso", usuariosIds));
            acoes.add(acaoPorUsuarios("autenticacao.cadastros_conta", usuariosIds));
            acoes.add(acaoPorUsuarios("autenticacao.recuperacoes_senha", usuariosIds));
            acoes.add(acaoPorUsuarios("seguranca.credenciais_atestacao_dispositivo", usuariosIds));
            acoes.add(acaoTokensDispositivoPorUsuarios(usuariosIds));
            acoes.add(acaoPorUsuarios("dispositivos.dispositivos_confiaveis", usuariosIds));
            acoes.add(acaoPorUsuarios("dispositivos.registros_dispositivo", usuariosIds));
            acoes.add(anonimizarPorUsuarios("seguranca.atestacoes_app_desafios", usuariosIds));
            acoes.add(anonimizarPorUsuarios("auditoria.operacoes_atestadas", usuariosIds));
            acoes.add(anonimizarPorUsuarios("auditoria.usuarios_historico", usuariosIds));
            acoes.add(acaoPorUsuariosCentrais("autenticacao.usuarios", usuariosIds));
            return;
        }
        preservados.add(preservarPorUsuarios("autenticacao.usuarios_formas_acesso", usuariosIds));
        preservados.add(preservarPorUsuarios("autenticacao.cadastros_conta", usuariosIds));
        preservados.add(preservarPorUsuarios("autenticacao.recuperacoes_senha", usuariosIds));
        preservados.add(preservarPorUsuarios("seguranca.credenciais_atestacao_dispositivo", usuariosIds));
        preservados.add(preservarTokensDispositivoPorUsuarios(usuariosIds));
        preservados.add(preservarPorUsuarios("dispositivos.dispositivos_confiaveis", usuariosIds));
        preservados.add(preservarPorUsuarios("dispositivos.registros_dispositivo", usuariosIds));
        preservados.add(preservarPorUsuarios("seguranca.atestacoes_app_desafios", usuariosIds));
        preservados.add(preservarPorUsuarios("auditoria.operacoes_atestadas", usuariosIds));
        preservados.add(preservarPorUsuarios("auditoria.usuarios_historico", usuariosIds));
        preservados.add(preservarPorUsuariosCentrais("autenticacao.usuarios", usuariosIds));
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta acaoPorUsuarios(final String tabela,
                                                                        final List<String> usuariosIds) {
        return acaoPorUsuariosColuna(tabela, "usuario_id", usuariosIds);
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta acaoPorUsuariosColuna(final String tabela,
                                                                              final String coluna,
                                                                              final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                tabela,
                contarPorIds(tabela, coluna, usuariosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta acaoPorUsuariosCentrais(final String tabela,
                                                                                final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                tabela,
                contarPorIds(tabela, "id", usuariosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta acaoPorVinculos(final String tabela,
                                                                        final List<String> vinculosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                tabela,
                contarPorIds(tabela, "usuario_cliente_id", vinculosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta anonimizarPorVinculos(final String tabela,
                                                                              final List<String> vinculosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "ANONIMIZAR",
                tabela,
                contarPorIds(tabela, "vinculo_id", vinculosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta anonimizarPorUsuarios(final String tabela,
                                                                              final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "ANONIMIZAR",
                tabela,
                contarPorIds(tabela, "usuario_id", usuariosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta acaoTokensDispositivoPorUsuarios(
            final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                "dispositivos.tokens_dispositivo",
                contarTokensDispositivoPorUsuarios(usuariosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta preservarPorUsuarios(final String tabela,
                                                                             final List<String> usuariosIds) {
        return preservarPorUsuariosColuna(tabela, "usuario_id", usuariosIds);
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta preservarPorUsuariosColuna(final String tabela,
                                                                                   final String coluna,
                                                                                   final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "PRESERVAR",
                tabela,
                contarPorIds(tabela, coluna, usuariosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta preservarPorUsuariosCentrais(final String tabela,
                                                                                     final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "PRESERVAR",
                tabela,
                contarPorIds(tabela, "id", usuariosIds)
        );
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta preservarTokensDispositivoPorUsuarios(
            final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "PRESERVAR",
                "dispositivos.tokens_dispositivo",
                contarTokensDispositivoPorUsuarios(usuariosIds)
        );
    }

    private long contarPorIds(final String tabela, final String coluna, final List<String> ids) {
        if (ids.isEmpty()) {
            return 0L;
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", ids.stream()
                .map(UUID::fromString)
                .toList());
        Long quantidade = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tabela + " WHERE " + coluna + " IN (:ids)",
                params,
                Long.class
        );
        return quantidade == null ? 0L : quantidade;
    }

    private long contarTokensDispositivoPorUsuarios(final List<String> usuariosIds) {
        if (usuariosIds.isEmpty()) {
            return 0L;
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", usuariosIds.stream()
                .map(UUID::fromString)
                .toList());
        Long quantidade = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dispositivos.tokens_dispositivo token
                WHERE token.dispositivo_id IN (
                        SELECT id FROM dispositivos.dispositivos_confiaveis WHERE usuario_id IN (:ids)
                    )
                   OR token.registro_dispositivo_id IN (
                        SELECT id FROM dispositivos.registros_dispositivo WHERE usuario_id IN (:ids)
                    )
                """, params, Long.class);
        return quantidade == null ? 0L : quantidade;
    }

    private void adicionarPreservados(final List<String> usuariosIds,
                                      final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados) {
        List<UUID> pessoasIds = resolverPessoasIds(usuariosIds);
        preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_IDENTIDADE,
                "PRESERVAR",
                "identidade.pessoas",
                contarIdentidadePorPessoa("identidade.pessoas", "id", pessoasIds)
        ));
        preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_IDENTIDADE,
                "PRESERVAR",
                "identidade.contatos_email",
                contarIdentidadePorPessoa("identidade.contatos_email", "pessoa_id", pessoasIds)
        ));
        preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "NAO_TOCAR",
                "catalogo.clientes_ecossistema",
                0L
        ));
    }

    private List<UUID> resolverPessoasIds(final List<String> usuariosIds) {
        if (usuariosIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", usuariosIds.stream()
                .map(UUID::fromString)
                .toList());
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT pessoa_id
                FROM autenticacao.usuarios
                WHERE id IN (:ids)
                """, params).stream()
                .map(linha -> linha.get("pessoa_id"))
                .filter(Objects::nonNull)
                .map(valor -> UUID.fromString(Objects.toString(valor)))
                .toList();
    }

    private long contarIdentidadePorPessoa(final String tabela, final String coluna, final List<UUID> pessoasIds) {
        if (pessoasIds.isEmpty()) {
            return 0L;
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", pessoasIds);
        Long quantidade = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tabela + " WHERE " + coluna + " IN (:ids)",
                params,
                Long.class
        );
        return quantidade == null ? 0L : quantidade;
    }

    private void adicionarPlanoKeycloak(final List<String> usuariosIds,
                                        final boolean usuarioCentralExclusivoDoProduto,
                                        final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
                                        final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
                                        final List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios) {
        if (usuariosIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", usuariosIds.stream()
                .map(UUID::fromString)
                .toList());
        List<Map<String, Object>> resultados = jdbcTemplate.queryForList("""
                SELECT id, sub_remoto
                FROM autenticacao.usuarios
                WHERE id IN (:ids)
                """, params);
        long usuariosComSubKeycloak = resultados.stream()
                .map(linha -> Objects.toString(linha.get("sub_remoto"), ""))
                .filter(StringUtils::hasText)
                .count();
        if (usuarioCentralExclusivoDoProduto) {
            acoes.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                    SISTEMA_KEYCLOAK,
                    "APAGAR",
                    "realm eickrono user_entity",
                    usuariosComSubKeycloak
            ));
        } else {
            preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                    SISTEMA_KEYCLOAK,
                    "PRESERVAR",
                    "realm eickrono user_entity",
                    usuariosComSubKeycloak
            ));
        }
        preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_KEYCLOAK,
                "NAO_TOCAR",
                "realm master admin",
                0L
        ));
        preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_KEYCLOAK,
                "NAO_TOCAR",
                "clients e identity providers globais",
                0L
        ));
        if (usuarioCentralExclusivoDoProduto && usuariosComSubKeycloak < usuariosIds.size()) {
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_KEYCLOAK,
                    "keycloak_sub_nao_resolvido",
                    "Um ou mais usuarios de autenticacao nao possuem sub_remoto para localizar o usuario Keycloak."
            ));
        }
    }

    private long contarVinculosAtivosForaDoAlvo(final List<String> usuariosIds, final List<String> vinculosIds) {
        if (usuariosIds.isEmpty() || vinculosIds.isEmpty()) {
            return 0L;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("usuariosIds", usuariosIds.stream().map(UUID::fromString).toList())
                .addValue("vinculosIds", vinculosIds.stream().map(UUID::fromString).toList());
        Long quantidade = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM autenticacao.usuarios_clientes_ecossistema
                WHERE usuario_id IN (:usuariosIds)
                  AND COALESCE(status_vinculo, '') <> 'REVOGADO'
                  AND id NOT IN (:vinculosIds)
                """, params, Long.class);
        return quantidade == null ? 0L : quantidade;
    }

    private void adicionarPlanoStorageAvatar(final List<String> vinculosIds,
                                             final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
                                             final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados) {
        acoes.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_STORAGE,
                "MATERIALIZAR_PENDENCIA",
                "identidade.avatar_usuario.storage_key",
                contarAvatares(vinculosIds, true)
        ));
        preservados.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_STORAGE,
                "NAO_APAGAR_URL_EXTERNA",
                "identidade.avatar_usuario.url_avatar sem storage_key",
                contarAvatares(vinculosIds, false)
        ));
    }

    private long contarAvatares(final List<String> vinculosIds, final boolean comStorageKey) {
        if (vinculosIds.isEmpty()) {
            return 0L;
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", vinculosIds.stream()
                .map(UUID::fromString)
                .toList());
        Long quantidade = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM identidade.avatar_usuario
                WHERE usuario_cliente_id IN (:ids)
                  AND removido_em IS NULL
                  AND storage_key IS %s NULL
                """.formatted(comStorageKey ? "NOT" : ""), params, Long.class);
        return quantidade == null ? 0L : quantidade;
    }

    private void adicionarPlanoProduto(final String produto,
                                       final ExclusaoCadastroProdutoApiRequest requisicao,
                                       final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
                                       final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
                                       final List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios) {
        ResolvedorExclusaoCadastroProdutoService resolvedor = resolvedoresProduto.stream()
                .filter(candidato -> candidato.suporta(produto))
                .findFirst()
                .orElse(null);
        if (resolvedor == null) {
            LOGGER.warn(
                    "exclusao_cadastro_produto_resolvedor_produto_ausente produto={} usuarioPublicoProduto={} perfilProdutoIdPresente={}",
                    produto,
                    requisicao.usuarioPublicoProduto(),
                    requisicao.perfilProdutoId() != null && !requisicao.perfilProdutoId().isBlank()
            );
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_PRODUTO,
                    "resolvedor_nao_implementado",
                    "Nenhum resolvedor de dryRun foi encontrado para o produto informado."
            ));
            return;
        }
        ResolvedorExclusaoCadastroProdutoService.Resultado resultado =
                resolvedor.simular(requisicao.usuarioPublicoProduto(), requisicao.perfilProdutoId());
        LOGGER.info(
                "exclusao_cadastro_produto_resolvedor_produto_dryrun_concluido produto={} usuarioPublicoProduto={} acoes={} preservados={} bloqueios={}",
                produto,
                requisicao.usuarioPublicoProduto(),
                resultado.acoes().size(),
                resultado.preservados().size(),
                resultado.bloqueios().size()
        );
        acoes.addAll(resultado.acoes());
        preservados.addAll(resultado.preservados());
        bloqueios.addAll(resultado.bloqueios());
    }

    private ExclusaoCadastroProdutoApiResposta resposta(final ExclusaoCadastroProdutoApiRequest requisicao,
                                                        final boolean dryRun,
                                                        final String correlacaoId,
                                                        final List<String> usuariosIds,
                                                        final List<String> vinculosIds,
                                                        final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
                                                        final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
                                                        final List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios) {
        return new ExclusaoCadastroProdutoApiResposta(
                correlacaoId,
                dryRun,
                new AlvosExclusaoCadastroProdutoApiResposta(
                        requisicao.produto(),
                        requisicao.usuarioPublicoProduto(),
                        requisicao.perfilProdutoId(),
                        usuariosIds,
                        vinculosIds
                ),
                List.copyOf(acoes),
                List.copyOf(preservados),
                List.copyOf(bloqueios)
        );
    }

    void registrarOperacao(final ExclusaoCadastroProdutoApiRequest requisicao,
                           final ExclusaoCadastroProdutoApiResposta resposta) {
        UUID exclusaoId = UUID.randomUUID();
        UUID correlacaoId = UUID.fromString(resposta.correlacaoId());
        String status = resposta.bloqueios().isEmpty()
                ? (resposta.dryRun() ? "PLANEJADA" : "EM_EXECUCAO")
                : "BLOQUEADA";
        jdbcTemplate.update("""
                INSERT INTO auditoria.exclusoes_cadastro_produto (
                    id,
                    correlacao_id,
                    produto,
                    usuario_publico_produto,
                    perfil_produto_id,
                    dry_run,
                    status,
                    motivo,
                    plano_json
                ) VALUES (
                    :id,
                    :correlacaoId,
                    :produto,
                    :usuarioPublicoProduto,
                    :perfilProdutoId,
                    :dryRun,
                    :status,
                    :motivo,
                    CAST(:planoJson AS JSONB)
                )
                """, new MapSqlParameterSource()
                .addValue("id", exclusaoId)
                .addValue("correlacaoId", correlacaoId)
                .addValue("produto", resposta.alvosResolvidos().produto())
                .addValue("usuarioPublicoProduto", resposta.alvosResolvidos().usuarioPublicoProduto(), Types.VARCHAR)
                .addValue("perfilProdutoId", uuidOuNulo(requisicao.perfilProdutoId()), Types.OTHER)
                .addValue("dryRun", resposta.dryRun())
                .addValue("status", status)
                .addValue("motivo", requisicao.motivo())
                .addValue("planoJson", json(resposta)));
        LOGGER.info(
                "exclusao_cadastro_produto_auditoria_operacao_inserida exclusaoId={} correlacaoId={} dryRun={} status={} acoes={} preservados={} bloqueios={}",
                exclusaoId,
                correlacaoId,
                resposta.dryRun(),
                status,
                resposta.acoes().size(),
                resposta.preservados().size(),
                resposta.bloqueios().size()
        );

        int ordem = 1;
        for (ItemPlanoExclusaoCadastroProdutoApiResposta acao : resposta.acoes()) {
            registrarEtapa(exclusaoId, ordem++, acao.sistema(), acao.tipo(), acao.recurso(), acao.quantidade(), "PLANEJADA", null, null);
        }
        for (ItemPlanoExclusaoCadastroProdutoApiResposta preservado : resposta.preservados()) {
            registrarEtapa(
                    exclusaoId,
                    ordem++,
                    preservado.sistema(),
                    preservado.tipo(),
                    preservado.recurso(),
                    preservado.quantidade(),
                    "IGNORADA",
                    null,
                    null
            );
        }
        for (BloqueioExclusaoCadastroProdutoApiResposta bloqueio : resposta.bloqueios()) {
            registrarEtapa(
                    exclusaoId,
                    ordem++,
                    bloqueio.sistema(),
                    "BLOQUEAR",
                    bloqueio.codigo(),
                    0L,
                    "BLOQUEADA",
                    bloqueio.codigo(),
                    bloqueio.detalhe()
            );
        }
    }

    private void registrarEtapa(final UUID exclusaoId,
                                final int ordem,
                                final String sistema,
                                final String tipo,
                                final String recurso,
                                final long quantidade,
                                final String status,
                                final String erroCodigo,
                                final String erroMensagem) {
        jdbcTemplate.update("""
                INSERT INTO auditoria.exclusoes_cadastro_produto_etapas (
                    id,
                    exclusao_id,
                    ordem,
                    sistema,
                    tipo,
                    recurso,
                    quantidade_planejada,
                    status,
                    erro_codigo,
                    erro_mensagem
                ) VALUES (
                    :id,
                    :exclusaoId,
                    :ordem,
                    :sistema,
                    :tipo,
                    :recurso,
                    :quantidadePlanejada,
                    :status,
                    :erroCodigo,
                    :erroMensagem
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("exclusaoId", exclusaoId)
                .addValue("ordem", ordem)
                .addValue("sistema", sistema)
                .addValue("tipo", tipo)
                .addValue("recurso", recurso)
                .addValue("quantidadePlanejada", quantidade)
                .addValue("status", status)
                .addValue("erroCodigo", erroCodigo, Types.VARCHAR)
                .addValue("erroMensagem", erroMensagem, Types.VARCHAR));
    }

    private String json(final Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nao foi possivel serializar auditoria do dryRun.", ex);
        }
    }

    private static String normalizar(final String valor) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID uuidOuNulo(final String valor) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ProdutoResolvido(long id, String codigo) {
    }

    private record VinculoProdutoResolvido(String vinculoId, String usuarioId) {
    }
}
