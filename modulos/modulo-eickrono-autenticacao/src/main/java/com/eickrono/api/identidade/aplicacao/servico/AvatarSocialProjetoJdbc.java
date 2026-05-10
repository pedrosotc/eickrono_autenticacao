package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.ApiAutenticadaException;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AvatarSocialProjetoJdbc {

    private static final String STATUS_USUARIO_ATIVO = "ATIVO";
    private static final String STATUS_VINCULO_ATIVO = "ATIVO";
    private static final String TIPO_FORMA_ACESSO_SOCIAL = "SOCIAL";
    private static final String TIPO_FORMA_ACESSO_EMAIL_SENHA = "EMAIL_SENHA";
    private static final String PROVEDOR_EMAIL = "EMAIL";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HexFormat hexFormat = HexFormat.of();

    public AvatarSocialProjetoJdbc(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
    }

    public void sincronizar(final String subRemoto,
                            final String emailPrincipal,
                            final Long clienteEcossistemaId,
                            final OffsetDateTime criadoEm,
                            final OffsetDateTime atualizadoEm,
                            final List<IdentidadeFederadaKeycloak> identidadesFederadas) {
        if (subRemoto == null || subRemoto.isBlank() || clienteEcossistemaId == null) {
            return;
        }
        OffsetDateTime criado = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        OffsetDateTime atualizado = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
        UUID usuarioId = assegurarUsuarioAtivo(subRemoto, emailPrincipal, clienteEcossistemaId, criado, atualizado);
        Set<String> provedoresAtivos = new HashSet<>();
        for (IdentidadeFederadaKeycloak identidade : Objects.requireNonNull(
                identidadesFederadas, "identidadesFederadas são obrigatórias")) {
            String provedor = identidade.provedor().getAliasFormaAcesso();
            String identificador = identidade.identificadorCanonico();
            provedoresAtivos.add(provedor);
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", gerarFormaAcessoId(TIPO_FORMA_ACESSO_SOCIAL, provedor, identificador))
                    .addValue("usuarioId", usuarioId)
                    .addValue("tipo", TIPO_FORMA_ACESSO_SOCIAL)
                    .addValue("provedor", provedor)
                    .addValue("identificadorExterno", identificador)
                    .addValue("principal", false)
                    .addValue("verificadoEm", atualizado)
                    .addValue("vinculadoEm", criado)
                    .addValue("desvinculadoEm", null)
                    .addValue("nomeExibicaoExterno", normalizarOpcional(identidade.nomeExibicaoExterno()))
                    .addValue("urlAvatarExterno", normalizarOpcional(identidade.urlAvatarExterno()))
                    .addValue(
                            "avatarExternoAtualizadoEm",
                            normalizarOpcional(identidade.urlAvatarExterno()) == null ? null : atualizado
                    );
            jdbcTemplate.update("""
                    INSERT INTO autenticacao.usuarios_formas_acesso (
                        id,
                        usuario_id,
                        email_id,
                        tipo,
                        provedor,
                        identificador_externo,
                        principal,
                        verificado_em,
                        vinculado_em,
                        desvinculado_em,
                        nome_exibicao_externo,
                        url_avatar_externo,
                        avatar_externo_atualizado_em
                    )
                    VALUES (
                        :id,
                        :usuarioId,
                        NULL,
                        :tipo,
                        :provedor,
                        :identificadorExterno,
                        :principal,
                        :verificadoEm,
                        :vinculadoEm,
                        :desvinculadoEm,
                        :nomeExibicaoExterno,
                        :urlAvatarExterno,
                        :avatarExternoAtualizadoEm
                    )
                    ON CONFLICT (tipo, provedor, identificador_externo) DO UPDATE
                    SET usuario_id = EXCLUDED.usuario_id,
                        principal = EXCLUDED.principal,
                        verificado_em = COALESCE(
                            EXCLUDED.verificado_em,
                            autenticacao.usuarios_formas_acesso.verificado_em
                        ),
                        desvinculado_em = NULL,
                        nome_exibicao_externo = COALESCE(
                            EXCLUDED.nome_exibicao_externo,
                            autenticacao.usuarios_formas_acesso.nome_exibicao_externo
                        ),
                        url_avatar_externo = EXCLUDED.url_avatar_externo,
                        avatar_externo_atualizado_em = CASE
                            WHEN EXCLUDED.url_avatar_externo IS NULL
                                THEN autenticacao.usuarios_formas_acesso.avatar_externo_atualizado_em
                            ELSE EXCLUDED.avatar_externo_atualizado_em
                        END
                    """, params);
        }

        MapSqlParameterSource paramsRemocao = new MapSqlParameterSource()
                .addValue("usuarioId", usuarioId)
                .addValue("tipo", TIPO_FORMA_ACESSO_SOCIAL)
                .addValue("desvinculadoEm", atualizado)
                .addValue("provedoresAtivos", provedoresAtivos);
        if (provedoresAtivos.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE autenticacao.usuarios_formas_acesso
                    SET desvinculado_em = :desvinculadoEm
                    WHERE usuario_id = :usuarioId
                      AND tipo = :tipo
                      AND desvinculado_em IS NULL
                    """, paramsRemocao);
        } else {
            jdbcTemplate.update("""
                    UPDATE autenticacao.usuarios_formas_acesso
                    SET desvinculado_em = :desvinculadoEm
                    WHERE usuario_id = :usuarioId
                      AND tipo = :tipo
                      AND desvinculado_em IS NULL
                      AND provedor NOT IN (:provedoresAtivos)
                    """, paramsRemocao);
        }
        limparAvatarSocialInvalido(usuarioId, clienteEcossistemaId, atualizado);
    }

    public PreferenciaAvatarProjeto buscarPreferencia(final String subRemoto,
                                                      final Long clienteEcossistemaId) {
        if (subRemoto == null || subRemoto.isBlank() || clienteEcossistemaId == null) {
            return PreferenciaAvatarProjeto.vazia();
        }
        UUID usuarioId = gerarUsuarioId(subRemoto);
        List<PreferenciaAvatarProjeto> preferencias = jdbcTemplate.query("""
                SELECT uce.avatar_preferido_origem,
                       uce.avatar_preferido_url,
                       ufa.provedor AS provedor_social
                FROM autenticacao.usuarios_clientes_ecossistema uce
                LEFT JOIN autenticacao.usuarios_formas_acesso ufa
                       ON ufa.id = uce.avatar_preferido_forma_acesso_id
                WHERE uce.usuario_id = :usuarioId
                  AND uce.cliente_ecossistema_id = :clienteEcossistemaId
                """,
                new MapSqlParameterSource()
                        .addValue("usuarioId", usuarioId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId),
                (rs, rowNum) -> new PreferenciaAvatarProjeto(
                        normalizarOpcional(rs.getString("avatar_preferido_origem")),
                        normalizarOpcional(rs.getString("avatar_preferido_url")),
                        normalizarOpcional(rs.getString("provedor_social"))));
        return preferencias.isEmpty() ? PreferenciaAvatarProjeto.vazia() : preferencias.getFirst();
    }

    public void definirAvatarSocial(final String subRemoto,
                                    final Long clienteEcossistemaId,
                                    final ProvedorVinculoSocial provedor,
                                    final OffsetDateTime atualizadoEm) {
        UUID usuarioId = localizarUsuarioIdObrigatorio(subRemoto);
        UUID formaAcessoId = jdbcTemplate.query("""
                SELECT id
                FROM autenticacao.usuarios_formas_acesso
                WHERE usuario_id = :usuarioId
                  AND tipo = :tipo
                  AND provedor = :provedor
                  AND desvinculado_em IS NULL
                ORDER BY vinculado_em DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("usuarioId", usuarioId)
                        .addValue("tipo", TIPO_FORMA_ACESSO_SOCIAL)
                        .addValue("provedor", provedor.getAliasFormaAcesso()),
                (rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiAutenticadaException.conflito(
                        "avatar_social_indisponivel",
                        "A rede social informada ainda nao possui foto disponivel para este projeto.",
                        Map.of("provedor", provedor.getAliasApi())
                ));
        List<String> urlsAvatar = jdbcTemplate.query("""
                SELECT url_avatar_externo
                FROM autenticacao.usuarios_formas_acesso
                WHERE id = :formaAcessoId
                """,
                new MapSqlParameterSource("formaAcessoId", formaAcessoId),
                (rs, rowNum) -> normalizarOpcional(rs.getString("url_avatar_externo")));
        String urlAvatar = urlsAvatar.isEmpty() ? null : urlsAvatar.getFirst();
        if (urlAvatar == null) {
            throw ApiAutenticadaException.conflito(
                    "avatar_social_indisponivel",
                    "A rede social informada ainda nao possui foto disponivel para este projeto.",
                    Map.of("provedor", provedor.getAliasApi())
            );
        }
        assegurarVinculoProjeto(
                usuarioId,
                Objects.requireNonNull(subRemoto, "subRemoto é obrigatório"),
                Objects.requireNonNull(clienteEcossistemaId, "clienteEcossistemaId é obrigatório"),
                Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório")
        );
        jdbcTemplate.update("""
                UPDATE autenticacao.usuarios_clientes_ecossistema
                SET avatar_preferido_origem = 'SOCIAL',
                    avatar_preferido_forma_acesso_id = :formaAcessoId,
                    avatar_preferido_url = :avatarPreferidoUrl,
                    avatar_preferido_atualizado_em = :atualizadoEm,
                    avatar_preferido_arquivo_id = NULL
                WHERE usuario_id = :usuarioId
                  AND cliente_ecossistema_id = :clienteEcossistemaId
                """,
                new MapSqlParameterSource()
                        .addValue("formaAcessoId", formaAcessoId)
                        .addValue("avatarPreferidoUrl", urlAvatar)
                        .addValue("atualizadoEm", atualizadoEm)
                        .addValue("usuarioId", usuarioId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId));
    }

    public void definirAvatarUrl(final String subRemoto,
                                 final Long clienteEcossistemaId,
                                 final String url,
                                 final OffsetDateTime atualizadoEm) {
        UUID usuarioId = localizarUsuarioIdObrigatorio(subRemoto);
        assegurarVinculoProjeto(
                usuarioId,
                Objects.requireNonNull(subRemoto, "subRemoto é obrigatório"),
                Objects.requireNonNull(clienteEcossistemaId, "clienteEcossistemaId é obrigatório"),
                Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório")
        );
        jdbcTemplate.update("""
                UPDATE autenticacao.usuarios_clientes_ecossistema
                SET avatar_preferido_origem = 'URL_EXTERNA',
                    avatar_preferido_forma_acesso_id = NULL,
                    avatar_preferido_url = :avatarPreferidoUrl,
                    avatar_preferido_atualizado_em = :atualizadoEm,
                    avatar_preferido_arquivo_id = NULL
                WHERE usuario_id = :usuarioId
                  AND cliente_ecossistema_id = :clienteEcossistemaId
                """,
                new MapSqlParameterSource()
                        .addValue("avatarPreferidoUrl", normalizarObrigatorio(url, "url"))
                        .addValue("atualizadoEm", atualizadoEm)
                        .addValue("usuarioId", usuarioId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId));
    }

    public void limparAvatarPreferido(final String subRemoto,
                                      final Long clienteEcossistemaId,
                                      final OffsetDateTime atualizadoEm) {
        UUID usuarioId = localizarUsuarioIdObrigatorio(subRemoto);
        assegurarVinculoProjeto(
                usuarioId,
                Objects.requireNonNull(subRemoto, "subRemoto é obrigatório"),
                Objects.requireNonNull(clienteEcossistemaId, "clienteEcossistemaId é obrigatório"),
                Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório")
        );
        jdbcTemplate.update("""
                UPDATE autenticacao.usuarios_clientes_ecossistema
                SET avatar_preferido_origem = 'NENHUM',
                    avatar_preferido_forma_acesso_id = NULL,
                    avatar_preferido_url = NULL,
                    avatar_preferido_atualizado_em = :atualizadoEm,
                    avatar_preferido_arquivo_id = NULL
                WHERE usuario_id = :usuarioId
                  AND cliente_ecossistema_id = :clienteEcossistemaId
                """,
                new MapSqlParameterSource()
                        .addValue("atualizadoEm", atualizadoEm)
                        .addValue("usuarioId", usuarioId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId));
    }

    private UUID assegurarUsuarioAtivo(final String subRemoto,
                                       final String emailPrincipal,
                                       final Long clienteEcossistemaId,
                                       final OffsetDateTime criadoEm,
                                       final OffsetDateTime atualizadoEm) {
        UUID usuarioId = gerarUsuarioId(subRemoto);
        UUID pessoaId = gerarPessoaId(subRemoto);
        jdbcTemplate.update("""
                INSERT INTO autenticacao.usuarios (
                    id,
                    pessoa_id,
                    sub_remoto,
                    status_global,
                    credencial_local_habilitada,
                    ultimo_login_em,
                    criado_em,
                    atualizado_em
                )
                VALUES (
                    :id,
                    :pessoaId,
                    :subRemoto,
                    :statusGlobal,
                    TRUE,
                    NULL,
                    :criadoEm,
                    :atualizadoEm
                )
                ON CONFLICT (sub_remoto) DO UPDATE
                SET pessoa_id = EXCLUDED.pessoa_id,
                    status_global = EXCLUDED.status_global,
                    credencial_local_habilitada = EXCLUDED.credencial_local_habilitada,
                    atualizado_em = EXCLUDED.atualizado_em
                """,
                new MapSqlParameterSource()
                        .addValue("id", usuarioId)
                        .addValue("pessoaId", pessoaId)
                        .addValue("subRemoto", subRemoto)
                        .addValue("statusGlobal", STATUS_USUARIO_ATIVO)
                        .addValue("criadoEm", criadoEm)
                        .addValue("atualizadoEm", atualizadoEm));
        assegurarVinculoProjeto(usuarioId, subRemoto, clienteEcossistemaId, atualizadoEm);
        if (emailPrincipal != null && !emailPrincipal.isBlank()) {
            String emailNormalizado = emailPrincipal.trim().toLowerCase(Locale.ROOT);
            jdbcTemplate.update("""
                    INSERT INTO autenticacao.usuarios_formas_acesso (
                        id,
                        usuario_id,
                        email_id,
                        tipo,
                        provedor,
                        identificador_externo,
                        principal,
                        verificado_em,
                        vinculado_em,
                        desvinculado_em
                    )
                    VALUES (
                        :id,
                        :usuarioId,
                        :emailId,
                        :tipo,
                        :provedor,
                        :identificadorExterno,
                        TRUE,
                        :verificadoEm,
                        :vinculadoEm,
                        NULL
                    )
                    ON CONFLICT (tipo, provedor, identificador_externo) DO UPDATE
                    SET usuario_id = EXCLUDED.usuario_id,
                        email_id = EXCLUDED.email_id,
                        principal = TRUE,
                        verificado_em = COALESCE(EXCLUDED.verificado_em,
                                                 autenticacao.usuarios_formas_acesso.verificado_em),
                        desvinculado_em = NULL
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", gerarFormaAcessoId(TIPO_FORMA_ACESSO_EMAIL_SENHA, PROVEDOR_EMAIL, emailNormalizado))
                            .addValue("usuarioId", usuarioId)
                            .addValue("emailId", gerarUuidDeterministico("identidade.email:" + emailNormalizado))
                            .addValue("tipo", TIPO_FORMA_ACESSO_EMAIL_SENHA)
                            .addValue("provedor", PROVEDOR_EMAIL)
                            .addValue("identificadorExterno", emailNormalizado)
                            .addValue("verificadoEm", atualizadoEm)
                            .addValue("vinculadoEm", criadoEm));
        }
        return usuarioId;
    }

    private void assegurarVinculoProjeto(final UUID usuarioId,
                                         final String subRemoto,
                                         final Long clienteEcossistemaId,
                                         final OffsetDateTime atualizadoEm) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", gerarUuidDeterministico(
                        "autenticacao.vinculo_cliente:" + subRemoto + ":" + clienteEcossistemaId))
                .addValue("usuarioId", usuarioId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("statusVinculo", STATUS_VINCULO_ATIVO)
                .addValue("vinculadoEm", atualizadoEm)
                .addValue("atualizadoEm", atualizadoEm);
        jdbcTemplate.update("""
                INSERT INTO autenticacao.usuarios_clientes_ecossistema (
                    id,
                    usuario_id,
                    cliente_ecossistema_id,
                    status_vinculo,
                    identificador_publico_cliente,
                    ultimo_acesso_em,
                    vinculado_em,
                    atualizado_em,
                    revogado_em,
                    motivo_revogacao
                )
                VALUES (
                    :id,
                    :usuarioId,
                    :clienteEcossistemaId,
                    :statusVinculo,
                    NULL,
                    NULL,
                    :vinculadoEm,
                    :atualizadoEm,
                    NULL,
                    NULL
                )
                ON CONFLICT (usuario_id, cliente_ecossistema_id) DO UPDATE
                SET status_vinculo = EXCLUDED.status_vinculo,
                    atualizado_em = EXCLUDED.atualizado_em,
                    revogado_em = NULL,
                    motivo_revogacao = NULL
                """, params);
    }

    private void limparAvatarSocialInvalido(final UUID usuarioId,
                                            final Long clienteEcossistemaId,
                                            final OffsetDateTime atualizadoEm) {
        jdbcTemplate.update("""
                UPDATE autenticacao.usuarios_clientes_ecossistema uce
                SET avatar_preferido_origem = 'NENHUM',
                    avatar_preferido_forma_acesso_id = NULL,
                    avatar_preferido_url = NULL,
                    avatar_preferido_atualizado_em = :atualizadoEm,
                    avatar_preferido_arquivo_id = NULL
                WHERE uce.usuario_id = :usuarioId
                  AND uce.cliente_ecossistema_id = :clienteEcossistemaId
                  AND uce.avatar_preferido_origem = 'SOCIAL'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM autenticacao.usuarios_formas_acesso ufa
                      WHERE ufa.id = uce.avatar_preferido_forma_acesso_id
                        AND ufa.desvinculado_em IS NULL
                        AND ufa.url_avatar_externo IS NOT NULL
                        AND btrim(ufa.url_avatar_externo) <> ''
                  )
                """,
                new MapSqlParameterSource()
                        .addValue("usuarioId", usuarioId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("atualizadoEm", atualizadoEm));
    }

    private UUID localizarUsuarioIdObrigatorio(final String subRemoto) {
        if (subRemoto == null || subRemoto.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject remoto é obrigatório.");
        }
        return jdbcTemplate.query("""
                SELECT id
                FROM autenticacao.usuarios
                WHERE sub_remoto = :subRemoto
                """,
                new MapSqlParameterSource("subRemoto", subRemoto),
                (rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "O usuario autenticado ainda nao possui registro multiapp ativo."
                ));
    }

    private UUID gerarUsuarioId(final String subRemoto) {
        return gerarUuidDeterministico("autenticacao.usuario:" + subRemoto);
    }

    private UUID gerarPessoaId(final String subRemoto) {
        return gerarUuidDeterministico("identidade.pessoa:" + subRemoto);
    }

    private UUID gerarFormaAcessoId(final String tipo,
                                    final String provedor,
                                    final String identificadorExterno) {
        return gerarUuidDeterministico(
                "autenticacao.forma_acesso:" + tipo + ":" + provedor + ":" + identificadorExterno);
    }

    private UUID gerarUuidDeterministico(final String material) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(material.getBytes(StandardCharsets.UTF_8));
            String hex = hexFormat.formatHex(digest);
            return UUID.fromString(
                    hex.substring(0, 8) + "-"
                            + hex.substring(8, 12) + "-"
                            + hex.substring(12, 16) + "-"
                            + hex.substring(16, 20) + "-"
                            + hex.substring(20, 32)
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo MD5 não disponível para geração de UUID determinístico.", ex);
        }
    }

    private String normalizarObrigatorio(final String valor, final String campo) {
        String texto = normalizarOpcional(valor);
        if (texto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " é obrigatório.");
        }
        return texto;
    }

    private String normalizarOpcional(final String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    public record PreferenciaAvatarProjeto(
            String origem,
            String url,
            String provedorSocial
    ) {
        public static PreferenciaAvatarProjeto vazia() {
            return new PreferenciaAvatarProjeto(null, null, null);
        }
    }
}
