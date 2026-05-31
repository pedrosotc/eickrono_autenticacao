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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ExclusaoCadastroProdutoDryRunService {

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
        validar(requisicao);

        List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes = new ArrayList<>();
        List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados = new ArrayList<>();
        List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios = new ArrayList<>();

        ProdutoResolvido produto = resolverProduto(requisicao.produto());
        if (produto == null) {
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_AUTENTICACAO,
                    "produto_nao_encontrado",
                    "Nenhum cliente de ecossistema ativo foi encontrado para o produto informado."
            ));
            ExclusaoCadastroProdutoApiResposta resposta =
                    resposta(requisicao, List.of(), List.of(), acoes, preservados, bloqueios);
            registrarDryRun(requisicao, resposta);
            return resposta;
        }

        List<VinculoProdutoResolvido> vinculos = resolverVinculosProduto(produto, requisicao);
        List<String> usuariosIds = vinculos.stream().map(VinculoProdutoResolvido::usuarioId).distinct().toList();
        List<String> vinculosIds = vinculos.stream().map(VinculoProdutoResolvido::vinculoId).distinct().toList();

        adicionarAcoesAutenticacao(usuariosIds, vinculosIds, acoes);
        adicionarPlanoKeycloak(usuariosIds, acoes, preservados, bloqueios);
        adicionarPlanoStorageAvatar(vinculosIds, acoes, preservados);
        adicionarPlanoProduto(produto.codigo(), requisicao, acoes, preservados, bloqueios);
        adicionarPreservados(usuariosIds, preservados);

        if (usuariosIds.isEmpty()) {
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_AUTENTICACAO,
                    "alvo_nao_resolvido",
                    "Nenhum vinculo usuario/produto foi encontrado para os identificadores informados."
            ));
        }

        ExclusaoCadastroProdutoApiResposta resposta = resposta(requisicao, usuariosIds, vinculosIds, acoes, preservados, bloqueios);
        registrarDryRun(requisicao, resposta);
        return resposta;
    }

    private void validar(final ExclusaoCadastroProdutoApiRequest requisicao) {
        if (!requisicao.dryRun()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "A primeira versao aceita apenas dryRun=true."
            );
        }
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
                WHERE LOWER(codigo) = :produto
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clienteEcossistemaId", produto.id())
                .addValue("usuarioPublicoProduto", normalizar(requisicao.usuarioPublicoProduto()), Types.VARCHAR)
                .addValue("vinculoId", uuidOuNulo(requisicao.perfilProdutoId()), Types.OTHER);
        List<Map<String, Object>> resultados = jdbcTemplate.queryForList("""
                SELECT vinculo.id AS vinculo_id,
                       vinculo.usuario_id AS usuario_id
                FROM autenticacao.usuarios_clientes_ecossistema vinculo
                WHERE vinculo.cliente_ecossistema_id = :clienteEcossistemaId
                  AND COALESCE(vinculo.status_vinculo, '') <> 'REVOGADO'
                  AND (
                        :usuarioPublicoProduto IS NULL
                        OR LOWER(vinculo.identificador_publico_cliente) = :usuarioPublicoProduto
                  )
                  AND (
                        :vinculoId IS NULL
                        OR vinculo.id = :vinculoId
                  )
                ORDER BY vinculo.vinculado_em DESC
                """, params);
        return resultados.stream()
                .map(linha -> new VinculoProdutoResolvido(
                        Objects.toString(linha.get("vinculo_id")),
                        Objects.toString(linha.get("usuario_id"))
                ))
                .toList();
    }

    private void adicionarAcoesAutenticacao(final List<String> usuariosIds,
                                            final List<String> vinculosIds,
                                            final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes) {
        acoes.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                "autenticacao.usuarios_clientes_ecossistema",
                vinculosIds.size()
        ));
        acoes.add(acaoPorUsuarios("autenticacao.usuarios_formas_acesso", usuariosIds));
        acoes.add(acaoPorUsuarios("autenticacao.recuperacoes_senha", usuariosIds));
        acoes.add(acaoPorUsuarios("dispositivos.dispositivos_confiaveis", usuariosIds));
        acoes.add(acaoPorUsuarios("dispositivos.registros_dispositivo", usuariosIds));
        acoes.add(acaoPorVinculos("identidade.avatar_usuario", vinculosIds));
        acoes.add(acaoPorUsuarios("autenticacao.usuarios", usuariosIds));
    }

    private ItemPlanoExclusaoCadastroProdutoApiResposta acaoPorUsuarios(final String tabela,
                                                                        final List<String> usuariosIds) {
        return new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_AUTENTICACAO,
                "APAGAR",
                tabela,
                contarPorIds(tabela, "usuario_id", usuariosIds)
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
        acoes.add(new ItemPlanoExclusaoCadastroProdutoApiResposta(
                SISTEMA_KEYCLOAK,
                "APAGAR",
                "realm eickrono user_entity",
                usuariosComSubKeycloak
        ));
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
        if (usuariosComSubKeycloak < usuariosIds.size()) {
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_KEYCLOAK,
                    "keycloak_sub_nao_resolvido",
                    "Um ou mais usuarios de autenticacao nao possuem sub_remoto para localizar o usuario Keycloak."
            ));
        }
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
            bloqueios.add(new BloqueioExclusaoCadastroProdutoApiResposta(
                    SISTEMA_PRODUTO,
                    "resolvedor_nao_implementado",
                    "Nenhum resolvedor de dryRun foi encontrado para o produto informado."
            ));
            return;
        }
        ResolvedorExclusaoCadastroProdutoService.Resultado resultado =
                resolvedor.simular(requisicao.usuarioPublicoProduto(), requisicao.perfilProdutoId());
        acoes.addAll(resultado.acoes());
        preservados.addAll(resultado.preservados());
        bloqueios.addAll(resultado.bloqueios());
    }

    private ExclusaoCadastroProdutoApiResposta resposta(final ExclusaoCadastroProdutoApiRequest requisicao,
                                                        final List<String> usuariosIds,
                                                        final List<String> vinculosIds,
                                                        final List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
                                                        final List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
                                                        final List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios) {
        return new ExclusaoCadastroProdutoApiResposta(
                UUID.randomUUID().toString(),
                true,
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

    private void registrarDryRun(final ExclusaoCadastroProdutoApiRequest requisicao,
                                 final ExclusaoCadastroProdutoApiResposta resposta) {
        UUID exclusaoId = UUID.randomUUID();
        UUID correlacaoId = UUID.fromString(resposta.correlacaoId());
        String status = resposta.bloqueios().isEmpty() ? "PLANEJADA" : "BLOQUEADA";
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
                    TRUE,
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
                .addValue("status", status)
                .addValue("motivo", requisicao.motivo())
                .addValue("planoJson", json(resposta)));

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
