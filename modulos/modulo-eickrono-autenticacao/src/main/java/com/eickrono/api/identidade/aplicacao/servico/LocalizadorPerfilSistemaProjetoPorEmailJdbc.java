package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.PerfilSistemaProjetoPorEmailResolvido;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalizadorPerfilSistemaProjetoPorEmailJdbc {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LocalizadorPerfilSistemaProjetoPorEmailJdbc(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
    }

    public Optional<PerfilSistemaProjetoPorEmailResolvido> localizar(final Long clienteEcossistemaId,
                                                                     final String emailInformado) {
        if (clienteEcossistemaId == null || !StringUtils.hasText(emailInformado)) {
            return Optional.empty();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("email", emailInformado.trim().toLowerCase(Locale.ROOT));
        return jdbcTemplate.query("""
                SELECT u.id AS usuario_id,
                       lower(ufa.identificador_externo) AS email_normalizado,
                       COALESCE(
                           NULLIF(trim(uce.identificador_publico_cliente), ''),
                           lower(ufa.identificador_externo)
                       ) AS login_sugerido
                FROM autenticacao.usuarios_formas_acesso ufa
                JOIN autenticacao.usuarios u
                  ON u.id = ufa.usuario_id
                JOIN autenticacao.usuarios_clientes_ecossistema uce
                  ON uce.usuario_id = ufa.usuario_id
                WHERE uce.cliente_ecossistema_id = :clienteEcossistemaId
                  AND uce.revogado_em IS NULL
                  AND ufa.desvinculado_em IS NULL
                  AND ufa.tipo = 'EMAIL_SENHA'
                  AND ufa.provedor = 'EMAIL'
                  AND lower(ufa.identificador_externo) = :email
                ORDER BY ufa.principal DESC,
                         uce.atualizado_em DESC
                LIMIT 1
                """, params, this::mapear).stream().findFirst();
    }

    private PerfilSistemaProjetoPorEmailResolvido mapear(final ResultSet rs, final int rowNum) throws SQLException {
        return new PerfilSistemaProjetoPorEmailResolvido(
                rs.getObject("usuario_id", UUID.class),
                rs.getString("email_normalizado"),
                rs.getString("login_sugerido")
        );
    }
}
