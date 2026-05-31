package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.VinculoSocialConfirmadoCadastro;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CadastroVinculoSocialConfirmadoJdbc {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CadastroVinculoSocialConfirmadoJdbc(final NamedParameterJdbcTemplate jdbcTemplate,
                                               final Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
    }

    public void registrar(final UUID cadastroId,
                          final List<VinculoSocialConfirmadoCadastro> vinculos) {
        if (cadastroId == null || vinculos == null || vinculos.isEmpty()) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                DELETE FROM autenticacao.cadastros_conta_vinculos_sociais_confirmados
                 WHERE cadastro_id = :cadastroId
                   AND consumido_em IS NULL
                """, new MapSqlParameterSource("cadastroId", cadastroId));
        for (VinculoSocialConfirmadoCadastro vinculo : vinculos) {
            if (vinculo == null) {
                continue;
            }
            String provedor = normalizarObrigatorio(vinculo.provedor(), "provedor");
            String identificadorExterno = normalizarObrigatorio(
                    vinculo.identificadorExterno(), "identificadorExterno");
            jdbcTemplate.update("""
                    INSERT INTO autenticacao.cadastros_conta_vinculos_sociais_confirmados (
                        id,
                        cadastro_id,
                        provedor,
                        identificador_externo,
                        nome_usuario_externo,
                        email_social,
                        nome_exibicao_externo,
                        url_avatar_externo,
                        avatar_preferido,
                        criado_em,
                        atualizado_em
                    ) VALUES (
                        :id,
                        :cadastroId,
                        :provedor,
                        :identificadorExterno,
                        :nomeUsuarioExterno,
                        :emailSocial,
                        :nomeExibicaoExterno,
                        :urlAvatarExterno,
                        :avatarPreferido,
                        :criadoEm,
                        :atualizadoEm
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("cadastroId", cadastroId)
                            .addValue("provedor", provedor)
                            .addValue("identificadorExterno", identificadorExterno)
                            .addValue("nomeUsuarioExterno", normalizarOpcional(vinculo.nomeUsuarioExterno()))
                            .addValue("emailSocial", normalizarEmail(vinculo.email()))
                            .addValue("nomeExibicaoExterno", normalizarOpcional(vinculo.nomeCompleto()))
                            .addValue("urlAvatarExterno", normalizarOpcional(vinculo.urlAvatarExterno()))
                            .addValue("avatarPreferido", vinculo.avatarPreferido())
                            .addValue("criadoEm", agora)
                            .addValue("atualizadoEm", agora));
        }
    }

    public List<VinculoSocialConfirmadoCadastro> listarAtivos(final UUID cadastroId) {
        if (cadastroId == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT provedor,
                       identificador_externo,
                       nome_usuario_externo,
                       email_social,
                       nome_exibicao_externo,
                       url_avatar_externo,
                       avatar_preferido
                  FROM autenticacao.cadastros_conta_vinculos_sociais_confirmados
                 WHERE cadastro_id = :cadastroId
                   AND consumido_em IS NULL
                 ORDER BY criado_em, provedor, identificador_externo
                """,
                new MapSqlParameterSource("cadastroId", cadastroId),
                this::mapear);
    }

    public void consumir(final UUID cadastroId) {
        if (cadastroId == null) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                UPDATE autenticacao.cadastros_conta_vinculos_sociais_confirmados
                   SET consumido_em = COALESCE(consumido_em, :consumidoEm),
                       atualizado_em = :atualizadoEm
                 WHERE cadastro_id = :cadastroId
                   AND consumido_em IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("cadastroId", cadastroId)
                        .addValue("consumidoEm", agora)
                        .addValue("atualizadoEm", agora));
    }

    private VinculoSocialConfirmadoCadastro mapear(final ResultSet rs, final int rowNum) throws SQLException {
        return new VinculoSocialConfirmadoCadastro(
                rs.getString("provedor"),
                rs.getString("identificador_externo"),
                rs.getString("nome_usuario_externo"),
                rs.getString("email_social"),
                rs.getString("nome_exibicao_externo"),
                rs.getString("url_avatar_externo"),
                rs.getBoolean("avatar_preferido")
        );
    }

    private static String normalizarObrigatorio(final String valor, final String campo) {
        String normalizado = normalizarOpcional(valor);
        if (normalizado == null) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return normalizado;
    }

    private static String normalizarEmail(final String valor) {
        String normalizado = normalizarOpcional(valor);
        return normalizado == null ? null : normalizado.toLowerCase(Locale.ROOT);
    }

    private static String normalizarOpcional(final String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isBlank() ? null : normalizado;
    }
}
