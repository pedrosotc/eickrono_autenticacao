package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResolvedorProjetoFluxoPublicoJdbc implements ResolvedorProjetoFluxoPublico {

    private static final RowMapper<ProjetoFluxoPublicoResolvido> ROW_MAPPER = new RowMapper<>() {
        @Override
        public ProjetoFluxoPublicoResolvido mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return new ProjetoFluxoPublicoResolvido(
                    rs.getLong("id"),
                    rs.getString("codigo"),
                    rs.getString("nome"),
                    rs.getString("tipo_produto_exibicao"),
                    rs.getString("produto_exibicao"),
                    rs.getString("canal_exibicao"),
                    rs.getBoolean("exige_validacao_telefone")
            );
        }
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ResolvedorProjetoFluxoPublicoJdbc(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
    }

    @Override
    public ProjetoFluxoPublicoResolvido resolverAtivo(final String aplicacaoId) {
        String codigoProjeto = normalizarCodigoProjeto(aplicacaoId);
        MapSqlParameterSource params = new MapSqlParameterSource("codigo", codigoProjeto);
        List<ProjetoFluxoPublicoResolvido> projetos = jdbcTemplate.query("""
                SELECT id,
                       codigo,
                       nome,
                       tipo_produto_exibicao,
                       produto_exibicao,
                       canal_exibicao,
                       exige_validacao_telefone
                FROM catalogo.clientes_ecossistema
                WHERE lower(codigo) = :codigo
                  AND ativo = TRUE
                """, params, ROW_MAPPER);
        if (projetos.isEmpty()) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "aplicacao_nao_habilitada",
                    "A aplicacao informada nao esta habilitada para este fluxo."
            );
        }
        return projetos.getFirst();
    }

    private String normalizarCodigoProjeto(final String aplicacaoId) {
        Objects.requireNonNull(aplicacaoId, "aplicacaoId é obrigatório");
        String valor = aplicacaoId.trim().toLowerCase(Locale.ROOT);
        if (valor.isBlank()) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "aplicacao_nao_habilitada",
                    "A aplicacao informada nao esta habilitada para este fluxo."
            );
        }
        return valor;
    }
}
