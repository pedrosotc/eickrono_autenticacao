package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.AvatarCadastroConfirmado;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CadastroAvatarConfirmadoJdbc {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final UploadAvatarCadastroServico uploadAvatarCadastroServico;

    @Autowired
    public CadastroAvatarConfirmadoJdbc(final NamedParameterJdbcTemplate jdbcTemplate,
                                        final Clock clock,
                                        final UploadAvatarCadastroServico uploadAvatarCadastroServico) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.uploadAvatarCadastroServico = Objects.requireNonNull(
                uploadAvatarCadastroServico,
                "uploadAvatarCadastroServico é obrigatório");
    }

    public CadastroAvatarConfirmadoJdbc(final NamedParameterJdbcTemplate jdbcTemplate,
                                        final Clock clock) {
        this(jdbcTemplate, clock, avatar -> avatar);
    }

    public void registrar(final UUID cadastroId,
                          final List<AvatarCadastroConfirmado> avatares) {
        if (cadastroId == null || avatares == null || avatares.isEmpty()) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                DELETE FROM autenticacao.cadastros_conta_avatares
                 WHERE cadastro_id = :cadastroId
                """, new MapSqlParameterSource("cadastroId", cadastroId));
        for (AvatarCadastroConfirmado avatar : avatares) {
            if (avatar == null) {
                continue;
            }
            AvatarCadastroConfirmado materializado = uploadAvatarCadastroServico.materializar(avatar);
            String origem = normalizarOrigem(materializado.origem());
            String urlAvatar = normalizarObrigatorio(materializado.urlAvatar(), "urlAvatar");
            int avataresInseridos = jdbcTemplate.update("""
                    INSERT INTO autenticacao.cadastros_conta_avatares (
                        id,
                        cadastro_id,
                        origem_id,
                        url_avatar,
                        storage_key,
                        content_type,
                        tamanho_bytes,
                        hash_conteudo,
                        versao,
                        preferido,
                        criado_em,
                        atualizado_em
                    )
                    SELECT :id,
                           :cadastroId,
                           origem.id,
                           :urlAvatar,
                           :storageKey,
                           :contentType,
                           :tamanhoBytes,
                           :hashConteudo,
                           :versao,
                           :preferido,
                           :criadoEm,
                           :atualizadoEm
                      FROM identidade.avatar_origens origem
                     WHERE origem.codigo = :origem
                       AND origem.ativo IS TRUE
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("cadastroId", cadastroId)
                            .addValue("origem", origem)
                            .addValue("urlAvatar", urlAvatar)
                            .addValue("storageKey", normalizarOpcional(materializado.storageKey()))
                            .addValue("contentType", normalizarOpcional(materializado.contentType()))
                            .addValue("tamanhoBytes", materializado.tamanhoBytes())
                            .addValue("hashConteudo", normalizarOpcional(materializado.hashConteudo()))
                            .addValue("versao", normalizarOpcional(materializado.versao()))
                            .addValue("preferido", materializado.preferido())
                            .addValue("criadoEm", agora)
                            .addValue("atualizadoEm", agora));
            if (avataresInseridos == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Origem de avatar confirmada desconhecida ou inativa.");
            }
        }
    }

    public void consumirParaUsuario(final UUID cadastroId,
                                    final String subjectRemoto,
                                    final Long clienteEcossistemaId,
                                    final OffsetDateTime atualizadoEm) {
        if (cadastroId == null || subjectRemoto == null || subjectRemoto.isBlank()
                || clienteEcossistemaId == null || atualizadoEm == null) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cadastroId", cadastroId)
                .addValue("subjectRemoto", subjectRemoto.trim())
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("atualizadoEm", atualizadoEm);

        jdbcTemplate.update("""
                UPDATE identidade.avatar_usuario avatar
                   SET preferido = FALSE,
                       atualizado_em = :atualizadoEm
                  FROM autenticacao.usuarios usuario
                  JOIN autenticacao.usuarios_clientes_ecossistema usuario_cliente
                    ON usuario_cliente.usuario_id = usuario.id
                   AND usuario_cliente.cliente_ecossistema_id = :clienteEcossistemaId
                 WHERE usuario.sub_remoto = :subjectRemoto
                   AND avatar.usuario_cliente_id = usuario_cliente.id
                   AND avatar.preferido IS TRUE
                   AND avatar.removido_em IS NULL
                   AND EXISTS (
                       SELECT 1
                         FROM autenticacao.cadastros_conta_avatares cadastro_avatar
                        WHERE cadastro_avatar.cadastro_id = :cadastroId
                          AND cadastro_avatar.preferido IS TRUE
                   )
                """, params);

        jdbcTemplate.update("""
                INSERT INTO identidade.avatar_usuario (
                    id,
                    usuario_cliente_id,
                    origem_id,
                    forma_acesso_id,
                    nome_exibicao_externo,
                    url_avatar,
                    storage_key,
                    content_type,
                    tamanho_bytes,
                    hash_conteudo,
                    versao,
                    preferido,
                    criado_em,
                    atualizado_em,
                    removido_em
                )
                SELECT cadastro_avatar.id,
                       usuario_cliente.id,
                       cadastro_avatar.origem_id,
                       NULL,
                       NULL,
                       cadastro_avatar.url_avatar,
                       cadastro_avatar.storage_key,
                       cadastro_avatar.content_type,
                       cadastro_avatar.tamanho_bytes,
                       cadastro_avatar.hash_conteudo,
                       cadastro_avatar.versao,
                       cadastro_avatar.preferido,
                       cadastro_avatar.criado_em,
                       :atualizadoEm,
                       NULL
                  FROM autenticacao.cadastros_conta_avatares cadastro_avatar
                  JOIN autenticacao.usuarios usuario
                    ON usuario.sub_remoto = :subjectRemoto
                  JOIN autenticacao.usuarios_clientes_ecossistema usuario_cliente
                    ON usuario_cliente.usuario_id = usuario.id
                   AND usuario_cliente.cliente_ecossistema_id = :clienteEcossistemaId
                 WHERE cadastro_avatar.cadastro_id = :cadastroId
                ON CONFLICT (id) DO UPDATE
                SET usuario_cliente_id = EXCLUDED.usuario_cliente_id,
                    origem_id = EXCLUDED.origem_id,
                    url_avatar = EXCLUDED.url_avatar,
                    storage_key = EXCLUDED.storage_key,
                    content_type = EXCLUDED.content_type,
                    tamanho_bytes = EXCLUDED.tamanho_bytes,
                    hash_conteudo = EXCLUDED.hash_conteudo,
                    versao = EXCLUDED.versao,
                    preferido = EXCLUDED.preferido,
                    atualizado_em = EXCLUDED.atualizado_em,
                    removido_em = NULL
                """, params);
    }

    private static String normalizarObrigatorio(final String valor, final String campo) {
        String normalizado = normalizarOpcional(valor);
        if (normalizado == null) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return normalizado;
    }

    private static String normalizarOrigem(final String valor) {
        return normalizarObrigatorio(valor, "origem").toUpperCase(Locale.ROOT);
    }

    private static String normalizarOpcional(final String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isBlank() ? null : normalizado;
    }
}
