package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.LoginSocialProjetoResolvido;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalizadorLoginSocialProjetoJdbc {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LocalizadorLoginSocialProjetoJdbc(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
    }

    public Optional<LoginSocialProjetoResolvido> localizar(final Long clienteEcossistemaId,
                                                           final String provedor,
                                                           final String identificadorExterno) {
        if (clienteEcossistemaId == null
                || !StringUtils.hasText(provedor)
                || !StringUtils.hasText(identificadorExterno)) {
            return Optional.empty();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("provedor", provedor.trim().toUpperCase(Locale.ROOT))
                .addValue("identificadorExterno", identificadorExterno.trim());
        return jdbcTemplate.query("""
                SELECT u.id AS usuario_id,
                       u.sub_remoto
                  FROM autenticacao.usuarios_formas_acesso ufa
                  JOIN autenticacao.usuarios u
                    ON u.id = ufa.usuario_id
                  JOIN autenticacao.usuarios_clientes_ecossistema uce
                    ON uce.usuario_id = u.id
                 WHERE uce.cliente_ecossistema_id = :clienteEcossistemaId
                   AND uce.revogado_em IS NULL
                   AND ufa.desvinculado_em IS NULL
                   AND ufa.tipo = 'SOCIAL'
                   AND ufa.provedor = :provedor
                   AND ufa.identificador_externo = :identificadorExterno
                   AND NULLIF(trim(u.sub_remoto), '') IS NOT NULL
                 ORDER BY uce.atualizado_em DESC
                 LIMIT 1
                """, params, this::mapear).stream().findFirst();
    }

    private LoginSocialProjetoResolvido mapear(final ResultSet rs, final int rowNum) throws SQLException {
        return new LoginSocialProjetoResolvido(
                rs.getObject("usuario_id", java.util.UUID.class),
                rs.getString("sub_remoto")
        );
    }
}
