package com.eickrono.api.identidade.infraestrutura.persistencia;

import com.eickrono.api.identidade.aplicacao.modelo.ControleIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.NovaPendenciaIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ParametrosPersistidosSchedulerIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import com.eickrono.api.identidade.dominio.repositorio.PendenciaIntegracaoProdutoRepositorio;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PendenciaIntegracaoProdutoJdbcRepositorio implements PendenciaIntegracaoProdutoRepositorio {

    private static final String SQL_REGISTRAR_OU_ATUALIZAR_PENDENCIA = """
            INSERT INTO autenticacao.pendencias_integracao_produto (
                id,
                cliente_ecossistema_id,
                tipo_operacao,
                uri_endpoint,
                metodo_http,
                payload_json,
                idempotency_key,
                versao_contrato,
                cadastro_id,
                pessoa_id_central,
                perfil_sistema_id,
                identificador_publico_sistema,
                status_pendencia,
                proxima_tentativa_em,
                codigo_ultimo_erro,
                mensagem_ultimo_erro,
                criado_em,
                atualizado_em
            )
            VALUES (
                :id,
                :clienteEcossistemaId,
                :tipoOperacao,
                :uriEndpoint,
                :metodoHttp,
                CAST(:payloadJson AS jsonb),
                :idempotencyKey,
                :versaoContrato,
                :cadastroId,
                :pessoaIdCentral,
                :perfilSistemaId,
                :identificadorPublicoSistema,
                :statusPendencia,
                :proximaTentativaEm,
                :codigoUltimoErro,
                :mensagemUltimoErro,
                :criadoEm,
                :atualizadoEm
            )
            ON CONFLICT (cliente_ecossistema_id, idempotency_key) DO UPDATE
            SET tipo_operacao = EXCLUDED.tipo_operacao,
                uri_endpoint = EXCLUDED.uri_endpoint,
                metodo_http = EXCLUDED.metodo_http,
                payload_json = EXCLUDED.payload_json,
                versao_contrato = EXCLUDED.versao_contrato,
                cadastro_id = EXCLUDED.cadastro_id,
                pessoa_id_central = EXCLUDED.pessoa_id_central,
                perfil_sistema_id = EXCLUDED.perfil_sistema_id,
                identificador_publico_sistema = EXCLUDED.identificador_publico_sistema,
                status_pendencia = EXCLUDED.status_pendencia,
                proxima_tentativa_em = EXCLUDED.proxima_tentativa_em,
                codigo_ultimo_erro = EXCLUDED.codigo_ultimo_erro,
                mensagem_ultimo_erro = EXCLUDED.mensagem_ultimo_erro,
                processando_por_instancia = NULL,
                processando_desde = NULL,
                atualizado_em = EXCLUDED.atualizado_em
            """;

    private static final String SQL_BUSCAR_PARAMETROS_GLOBAIS = """
            SELECT
                habilitado,
                tempo_entre_tentativas_segundos,
                quantidade_maxima_tentativas,
                quantidade_maxima_itens_por_ciclo,
                timeout_sondagem_millis,
                timeout_entrega_millis
            FROM autenticacao.parametros_scheduler_integracao_produto
            WHERE id = 1
            """;

    private static final String SQL_RECUPERAR_PENDENCIAS_ABANDONADAS = """
            UPDATE autenticacao.pendencias_integracao_produto
            SET status_pendencia = 'AGUARDANDO_NOVA_TENTATIVA',
                processando_por_instancia = NULL,
                processando_desde = NULL,
                codigo_ultimo_erro = 'PROCESSAMENTO_ABANDONADO',
                mensagem_ultimo_erro = 'Item recuperado apos timeout de processamento',
                proxima_tentativa_em = :novaTentativa,
                atualizado_em = :agora
            WHERE status_pendencia = 'EM_PROCESSAMENTO'
              AND processando_desde < :limiteProcessamento
            """;

    private static final String SQL_RESERVAR_PENDENCIAS_PROCESSAVEIS = """
            WITH lote AS (
                SELECT p.id
                FROM autenticacao.pendencias_integracao_produto p
                WHERE p.status_pendencia IN ('PENDENTE_ENVIO', 'AGUARDANDO_NOVA_TENTATIVA')
                  AND p.proxima_tentativa_em <= :referencia
                ORDER BY p.proxima_tentativa_em ASC, p.criado_em ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :limite
            )
            UPDATE autenticacao.pendencias_integracao_produto p
            SET status_pendencia = 'EM_PROCESSAMENTO',
                processando_por_instancia = :nomeInstancia,
                processando_desde = :referencia,
                atualizado_em = :referencia
            FROM lote
            WHERE p.id = lote.id
            RETURNING
                p.id,
                p.cliente_ecossistema_id,
                p.tipo_operacao,
                p.uri_endpoint,
                p.metodo_http,
                p.payload_json,
                p.idempotency_key,
                p.versao_contrato,
                p.cadastro_id,
                p.pessoa_id_central,
                p.perfil_sistema_id,
                p.identificador_publico_sistema,
                p.status_pendencia,
                p.tentativas_realizadas
            """;

    private static final String SQL_BUSCAR_CONTROLE_PRODUTO = """
            SELECT
                cliente_ecossistema_id,
                escritas_internas_habilitadas,
                produto_em_manutencao,
                inicio_manutencao,
                fim_manutencao,
                motivo_manutencao,
                tempo_entre_tentativas_segundos_override,
                quantidade_maxima_tentativas_override,
                timeout_sondagem_millis_override,
                timeout_entrega_millis_override
            FROM autenticacao.controles_integracao_produto
            WHERE cliente_ecossistema_id = :clienteEcossistemaId
            """;

    private static final String SQL_MARCAR_PAUSADO_MANUTENCAO = """
            UPDATE autenticacao.pendencias_integracao_produto
            SET status_pendencia = 'PAUSADO_MANUTENCAO',
                codigo_ultimo_erro = 'PRODUTO_EM_MANUTENCAO',
                mensagem_ultimo_erro = :mensagemUltimoErro,
                processando_por_instancia = NULL,
                processando_desde = NULL,
                atualizado_em = :agora
            WHERE id = :pendenciaId
            """;

    private static final String SQL_REAGENDAR = """
            UPDATE autenticacao.pendencias_integracao_produto
            SET status_pendencia = 'AGUARDANDO_NOVA_TENTATIVA',
                tentativas_realizadas = tentativas_realizadas + 1,
                ultima_tentativa_em = :agora,
                proxima_tentativa_em = :proximaTentativa,
                codigo_ultimo_erro = :codigoUltimoErro,
                mensagem_ultimo_erro = :mensagemUltimoErro,
                processando_por_instancia = NULL,
                processando_desde = NULL,
                atualizado_em = :agora
            WHERE id = :pendenciaId
            """;

    private static final String SQL_ESCALAR = """
            UPDATE autenticacao.pendencias_integracao_produto
            SET status_pendencia = 'FALHA_ESCALADA',
                tentativas_realizadas = tentativas_realizadas + 1,
                ultima_tentativa_em = :agora,
                codigo_ultimo_erro = :codigoUltimoErro,
                mensagem_ultimo_erro = :mensagemUltimoErro,
                processando_por_instancia = NULL,
                processando_desde = NULL,
                atualizado_em = :agora
            WHERE id = :pendenciaId
            """;

    private static final String SQL_REMOVER = """
            DELETE FROM autenticacao.pendencias_integracao_produto
            WHERE id = :pendenciaId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PendenciaIntegracaoProdutoJdbcRepositorio(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void registrarOuAtualizar(final NovaPendenciaIntegracaoProduto novaPendencia) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", novaPendencia.id())
                .addValue("clienteEcossistemaId", novaPendencia.clienteEcossistemaId())
                .addValue("tipoOperacao", novaPendencia.tipoOperacao())
                .addValue("uriEndpoint", novaPendencia.uriEndpoint())
                .addValue("metodoHttp", novaPendencia.metodoHttp())
                .addValue("payloadJson", novaPendencia.payloadJson())
                .addValue("idempotencyKey", novaPendencia.idempotencyKey())
                .addValue("versaoContrato", novaPendencia.versaoContrato())
                .addValue("cadastroId", novaPendencia.cadastroId())
                .addValue("pessoaIdCentral", novaPendencia.pessoaIdCentral())
                .addValue("perfilSistemaId", novaPendencia.perfilSistemaId())
                .addValue("identificadorPublicoSistema", novaPendencia.identificadorPublicoSistema())
                .addValue("statusPendencia", novaPendencia.statusPendencia())
                .addValue("proximaTentativaEm", novaPendencia.proximaTentativaEm())
                .addValue("codigoUltimoErro", novaPendencia.codigoUltimoErro())
                .addValue("mensagemUltimoErro", novaPendencia.mensagemUltimoErro())
                .addValue("criadoEm", novaPendencia.criadoEm())
                .addValue("atualizadoEm", novaPendencia.atualizadoEm());
        jdbcTemplate.update(SQL_REGISTRAR_OU_ATUALIZAR_PENDENCIA, params);
    }

    @Override
    public Optional<ParametrosPersistidosSchedulerIntegracaoProduto> buscarParametrosGlobais() {
        try {
            ParametrosPersistidosSchedulerIntegracaoProduto parametros = jdbcTemplate.queryForObject(
                    SQL_BUSCAR_PARAMETROS_GLOBAIS,
                    new MapSqlParameterSource(),
                    this::mapearParametrosGlobais);
            return Optional.ofNullable(parametros);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public int recuperarPendenciasEmProcessamentoAbandonadas(final OffsetDateTime limiteProcessamento,
                                                             final OffsetDateTime novaTentativa) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limiteProcessamento", limiteProcessamento)
                .addValue("novaTentativa", novaTentativa)
                .addValue("agora", novaTentativa);
        return jdbcTemplate.update(SQL_RECUPERAR_PENDENCIAS_ABANDONADAS, params);
    }

    @Override
    public List<PendenciaIntegracaoProduto> reservarPendenciasProcessaveis(final String nomeInstancia,
                                                                           final int quantidadeMaximaItensPorCiclo,
                                                                           final OffsetDateTime referencia) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nomeInstancia", nomeInstancia)
                .addValue("limite", quantidadeMaximaItensPorCiclo)
                .addValue("referencia", referencia);
        return jdbcTemplate.query(SQL_RESERVAR_PENDENCIAS_PROCESSAVEIS, params, this::mapearPendencia);
    }

    @Override
    public Optional<ControleIntegracaoProduto> buscarControleProduto(final long clienteEcossistemaId) {
        try {
            ControleIntegracaoProduto controle = jdbcTemplate.queryForObject(
                    SQL_BUSCAR_CONTROLE_PRODUTO,
                    new MapSqlParameterSource("clienteEcossistemaId", clienteEcossistemaId),
                    this::mapearControleProduto);
            return Optional.ofNullable(controle);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void marcarPausadoManutencao(final UUID pendenciaId, final String motivoManutencao) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pendenciaId", pendenciaId)
                .addValue("mensagemUltimoErro", motivoManutencao)
                .addValue("agora", OffsetDateTime.now());
        jdbcTemplate.update(SQL_MARCAR_PAUSADO_MANUTENCAO, params);
    }

    @Override
    public void reagendar(final UUID pendenciaId,
                          final OffsetDateTime proximaTentativa,
                          final String codigoUltimoErro,
                          final String mensagemUltimoErro) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pendenciaId", pendenciaId)
                .addValue("proximaTentativa", proximaTentativa)
                .addValue("codigoUltimoErro", codigoUltimoErro)
                .addValue("mensagemUltimoErro", mensagemUltimoErro)
                .addValue("agora", OffsetDateTime.now());
        jdbcTemplate.update(SQL_REAGENDAR, params);
    }

    @Override
    public void escalar(final UUID pendenciaId,
                        final String codigoUltimoErro,
                        final String mensagemUltimoErro) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pendenciaId", pendenciaId)
                .addValue("codigoUltimoErro", codigoUltimoErro)
                .addValue("mensagemUltimoErro", mensagemUltimoErro)
                .addValue("agora", OffsetDateTime.now());
        jdbcTemplate.update(SQL_ESCALAR, params);
    }

    @Override
    public void remover(final UUID pendenciaId) {
        jdbcTemplate.update(SQL_REMOVER, new MapSqlParameterSource("pendenciaId", pendenciaId));
    }

    private ParametrosPersistidosSchedulerIntegracaoProduto mapearParametrosGlobais(final ResultSet rs,
                                                                                    final int rowNum)
            throws SQLException {
        return new ParametrosPersistidosSchedulerIntegracaoProduto(
                rs.getBoolean("habilitado"),
                rs.getInt("tempo_entre_tentativas_segundos"),
                rs.getInt("quantidade_maxima_tentativas"),
                rs.getInt("quantidade_maxima_itens_por_ciclo"),
                rs.getInt("timeout_sondagem_millis"),
                rs.getInt("timeout_entrega_millis")
        );
    }

    private PendenciaIntegracaoProduto mapearPendencia(final ResultSet rs, final int rowNum) throws SQLException {
        return new PendenciaIntegracaoProduto(
                rs.getObject("id", UUID.class),
                rs.getLong("cliente_ecossistema_id"),
                rs.getString("tipo_operacao"),
                rs.getString("uri_endpoint"),
                rs.getString("metodo_http"),
                rs.getString("payload_json"),
                rs.getString("idempotency_key"),
                rs.getString("versao_contrato"),
                rs.getObject("cadastro_id", UUID.class),
                rs.getObject("pessoa_id_central", Long.class),
                rs.getString("perfil_sistema_id"),
                rs.getString("identificador_publico_sistema"),
                rs.getString("status_pendencia"),
                rs.getInt("tentativas_realizadas")
        );
    }

    private ControleIntegracaoProduto mapearControleProduto(final ResultSet rs, final int rowNum) throws SQLException {
        return new ControleIntegracaoProduto(
                rs.getLong("cliente_ecossistema_id"),
                rs.getBoolean("escritas_internas_habilitadas"),
                rs.getBoolean("produto_em_manutencao"),
                rs.getObject("inicio_manutencao", OffsetDateTime.class),
                rs.getObject("fim_manutencao", OffsetDateTime.class),
                rs.getString("motivo_manutencao"),
                rs.getObject("tempo_entre_tentativas_segundos_override", Integer.class),
                rs.getObject("quantidade_maxima_tentativas_override", Integer.class),
                rs.getObject("timeout_sondagem_millis_override", Integer.class),
                rs.getObject("timeout_entrega_millis_override", Integer.class)
        );
    }
}
