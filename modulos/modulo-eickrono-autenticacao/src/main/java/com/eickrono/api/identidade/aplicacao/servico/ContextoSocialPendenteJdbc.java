package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ContextoSocialPendenteJdbc {

    private static final String MODO_ABRIR_CADASTRO = "ABRIR_CADASTRO";
    private static final String MODO_ENTRAR_E_VINCULAR = "ENTRAR_E_VINCULAR";
    private static final int TENTATIVAS_MAXIMAS_PADRAO = 3;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ContextoSocialPendenteJdbc(final NamedParameterJdbcTemplate jdbcTemplate,
                                      final Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
    }

    public UUID registrarOuAtualizar(final ProjetoFluxoPublicoResolvido projeto,
                                     final String provedor,
                                     final String identificadorExterno,
                                     final String emailSocial,
                                     final String nomeUsuarioExterno,
                                     final String nomeExibicaoExterno,
                                     final String urlAvatarExterno,
                                     final UUID perfilSistemaIdSugerido,
                                     final String identificadorPublicoSistemaSugerido) {
        Objects.requireNonNull(projeto, "projeto é obrigatório");
        String provedorNormalizado = normalizarObrigatorio(provedor, "provedor");
        String identificadorNormalizado = normalizarObrigatorio(identificadorExterno, "identificadorExterno");
        String emailNormalizado = normalizarEmail(emailSocial);
        String nomeUsuarioExternoNormalizado = normalizarOpcional(nomeUsuarioExterno);
        String nomeExibicaoExternoNormalizado = normalizarOpcional(nomeExibicaoExterno);
        String urlAvatarExternoNormalizado = normalizarOpcional(urlAvatarExterno);
        String identificadorPublicoSistemaNormalizado = normalizarOpcional(identificadorPublicoSistemaSugerido);
        String modoPendente = perfilSistemaIdSugerido == null
                ? MODO_ABRIR_CADASTRO
                : MODO_ENTRAR_E_VINCULAR;

        OffsetDateTime agora = OffsetDateTime.now(clock);
        OffsetDateTime expiraEm = agora.plusMinutes(15);
        Optional<UUID> contextoAtivo = buscarContextoAtivo(
                projeto.clienteEcossistemaId(),
                provedorNormalizado,
                identificadorNormalizado
        );
        if (contextoAtivo.isPresent()) {
            UUID contextoId = contextoAtivo.get();
            jdbcTemplate.update("""
                    UPDATE autenticacao.contextos_sociais_pendentes
                       SET email_social_normalizado = :emailSocialNormalizado,
                           nome_usuario_externo = :nomeUsuarioExterno,
                           nome_exibicao_externo = :nomeExibicaoExterno,
                           url_avatar_externo = :urlAvatarExterno,
                           usuario_id_sugerido = :perfilSistemaIdSugerido,
                           login_sugerido = :identificadorPublicoSistemaSugerido,
                           modo_pendente = :modoPendente,
                           tentativas_falhas = 0,
                           tentativas_maximas = :tentativasMaximas,
                           expira_em = :expiraEm,
                           cancelado_em = NULL,
                           consumido_em = NULL,
                           motivo_cancelamento = NULL,
                           atualizado_em = :atualizadoEm
                     WHERE id = :id
                    """, parametrosBase(
                    contextoId,
                    projeto.clienteEcossistemaId(),
                    provedorNormalizado,
                    identificadorNormalizado,
                    emailNormalizado,
                    nomeUsuarioExternoNormalizado,
                    nomeExibicaoExternoNormalizado,
                    urlAvatarExternoNormalizado,
                    perfilSistemaIdSugerido,
                    identificadorPublicoSistemaNormalizado,
                    modoPendente,
                    expiraEm,
                    agora
            ));
            return contextoId;
        }

        UUID contextoId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO autenticacao.contextos_sociais_pendentes (
                    id,
                    cliente_ecossistema_id,
                    provedor,
                    identificador_externo,
                    email_social_normalizado,
                    nome_usuario_externo,
                    nome_exibicao_externo,
                    url_avatar_externo,
                    usuario_id_sugerido,
                    login_sugerido,
                    modo_pendente,
                    tentativas_falhas,
                    tentativas_maximas,
                    expira_em,
                    criado_em,
                    atualizado_em
                ) VALUES (
                    :id,
                    :clienteEcossistemaId,
                    :provedor,
                    :identificadorExterno,
                    :emailSocialNormalizado,
                    :nomeUsuarioExterno,
                    :nomeExibicaoExterno,
                    :urlAvatarExterno,
                    :perfilSistemaIdSugerido,
                    :identificadorPublicoSistemaSugerido,
                    :modoPendente,
                    0,
                    :tentativasMaximas,
                    :expiraEm,
                    :criadoEm,
                    :atualizadoEm
                )
                """, parametrosBase(
                contextoId,
                projeto.clienteEcossistemaId(),
                provedorNormalizado,
                identificadorNormalizado,
                emailNormalizado,
                nomeUsuarioExternoNormalizado,
                nomeExibicaoExternoNormalizado,
                urlAvatarExternoNormalizado,
                perfilSistemaIdSugerido,
                identificadorPublicoSistemaNormalizado,
                modoPendente,
                expiraEm,
                agora
        ));
        return contextoId;
    }

    public Optional<ContextoSocialPendenteAtivo> buscarAtivo(final UUID contextoId,
                                                             final Long clienteEcossistemaId) {
        if (contextoId == null || clienteEcossistemaId == null) {
            return Optional.empty();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", contextoId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId);
        return jdbcTemplate.query("""
                SELECT id,
                       cliente_ecossistema_id,
                       usuario_id_sugerido,
                       login_sugerido,
                       modo_pendente,
                       tentativas_falhas,
                       tentativas_maximas
                  FROM autenticacao.contextos_sociais_pendentes
                 WHERE id = :id
                   AND cliente_ecossistema_id = :clienteEcossistemaId
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                   AND expira_em > now()
                """, params, this::mapearContextoAtivo).stream().findFirst();
    }

    public void cancelar(final UUID contextoId,
                         final Long clienteEcossistemaId,
                         final String motivoCancelamento) {
        if (contextoId == null || clienteEcossistemaId == null) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                UPDATE autenticacao.contextos_sociais_pendentes
                   SET cancelado_em = COALESCE(cancelado_em, :canceladoEm),
                       motivo_cancelamento = :motivoCancelamento,
                       atualizado_em = :atualizadoEm
                 WHERE id = :id
                   AND cliente_ecossistema_id = :clienteEcossistemaId
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("id", contextoId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("canceladoEm", agora)
                        .addValue("motivoCancelamento", normalizarObrigatorio(motivoCancelamento, "motivoCancelamento"))
                        .addValue("atualizadoEm", agora));
    }

    public ResultadoFalhaEntrarEVincular registrarFalhaEntrarEVincular(final UUID contextoId,
                                                                       final Long clienteEcossistemaId) {
        if (contextoId == null || clienteEcossistemaId == null) {
            return ResultadoFalhaEntrarEVincular.inexistente();
        }
        Optional<ContextoSocialPendenteAtivo> contextoOpt = buscarAtivo(contextoId, clienteEcossistemaId);
        if (contextoOpt.isEmpty()) {
            return ResultadoFalhaEntrarEVincular.inexistente();
        }
        ContextoSocialPendenteAtivo contexto = contextoOpt.orElseThrow();
        int tentativasAtualizadas = contexto.tentativasFalhas() + 1;
        boolean limiteAtingido = tentativasAtualizadas >= contexto.tentativasMaximas();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                UPDATE autenticacao.contextos_sociais_pendentes
                   SET tentativas_falhas = :tentativasFalhas,
                       cancelado_em = CASE WHEN :limiteAtingido THEN COALESCE(cancelado_em, :canceladoEm) ELSE cancelado_em END,
                       motivo_cancelamento = CASE WHEN :limiteAtingido THEN :motivoCancelamento ELSE motivo_cancelamento END,
                       atualizado_em = :atualizadoEm
                 WHERE id = :id
                   AND cliente_ecossistema_id = :clienteEcossistemaId
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("id", contextoId)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("tentativasFalhas", tentativasAtualizadas)
                        .addValue("limiteAtingido", limiteAtingido)
                        .addValue("canceladoEm", agora)
                        .addValue("motivoCancelamento", "LIMITE_TENTATIVAS_AUTENTICACAO")
                        .addValue("atualizadoEm", agora));
        return new ResultadoFalhaEntrarEVincular(
                true,
                tentativasAtualizadas,
                contexto.tentativasMaximas(),
                limiteAtingido
        );
    }

    public boolean consumirSeCompativel(final UUID contextoId,
                                        final String emailAutenticado) {
        if (contextoId == null || !StringUtils.hasText(emailAutenticado)) {
            return false;
        }
        Optional<ContextoSocialPendenteAtivo> contextoOpt = buscarAtivoPorId(contextoId);
        if (contextoOpt.isEmpty()) {
            return false;
        }
        ContextoSocialPendenteAtivo contexto = contextoOpt.orElseThrow();
        if (contexto.perfilSistemaIdSugerido() == null) {
            return false;
        }
        Optional<UUID> usuarioIdAtual = localizarUsuarioProjetoPorEmail(
                contexto.clienteEcossistemaId(),
                emailAutenticado
        );
        if (usuarioIdAtual.isEmpty() || !contexto.perfilSistemaIdSugerido().equals(usuarioIdAtual.orElseThrow())) {
            return false;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        int atualizados = jdbcTemplate.update("""
                UPDATE autenticacao.contextos_sociais_pendentes
                   SET consumido_em = COALESCE(consumido_em, :consumidoEm),
                       atualizado_em = :atualizadoEm
                 WHERE id = :id
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("id", contextoId)
                        .addValue("consumidoEm", agora)
                        .addValue("atualizadoEm", agora));
        return atualizados > 0;
    }

    public List<ContextoSocialPendenteCadastro> listarPendentesAberturaCadastro(final Long clienteEcossistemaId,
                                                                                final String emailAutenticado) {
        if (clienteEcossistemaId == null || !StringUtils.hasText(emailAutenticado)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id,
                       provedor,
                       identificador_externo,
                       nome_usuario_externo,
                       nome_exibicao_externo,
                       url_avatar_externo
                  FROM autenticacao.contextos_sociais_pendentes
                 WHERE cliente_ecossistema_id = :clienteEcossistemaId
                   AND modo_pendente = :modoPendente
                   AND email_social_normalizado = :email
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                   AND expira_em > now()
                 ORDER BY atualizado_em ASC, criado_em ASC
                """,
                new MapSqlParameterSource()
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("modoPendente", MODO_ABRIR_CADASTRO)
                        .addValue("email", emailAutenticado.trim().toLowerCase(Locale.ROOT)),
                this::mapearContextoCadastro);
    }

    public boolean consumir(final UUID contextoId) {
        if (contextoId == null) {
            return false;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        int atualizados = jdbcTemplate.update("""
                UPDATE autenticacao.contextos_sociais_pendentes
                   SET consumido_em = COALESCE(consumido_em, :consumidoEm),
                       atualizado_em = :atualizadoEm
                 WHERE id = :id
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("id", contextoId)
                        .addValue("consumidoEm", agora)
                        .addValue("atualizadoEm", agora));
        return atualizados > 0;
    }

    private Optional<UUID> buscarContextoAtivo(final Long clienteEcossistemaId,
                                               final String provedor,
                                               final String identificadorExterno) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("provedor", provedor)
                .addValue("identificadorExterno", identificadorExterno);
        return jdbcTemplate.query("""
                SELECT id
                  FROM autenticacao.contextos_sociais_pendentes
                 WHERE cliente_ecossistema_id = :clienteEcossistemaId
                   AND provedor = :provedor
                   AND identificador_externo = :identificadorExterno
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                 ORDER BY atualizado_em DESC
                 LIMIT 1
                """, params, this::mapearUuid).stream().findFirst();
    }

    private Optional<ContextoSocialPendenteAtivo> buscarAtivoPorId(final UUID contextoId) {
        return jdbcTemplate.query("""
                SELECT id,
                       cliente_ecossistema_id,
                       usuario_id_sugerido,
                       login_sugerido,
                       modo_pendente,
                       tentativas_falhas,
                       tentativas_maximas
                  FROM autenticacao.contextos_sociais_pendentes
                 WHERE id = :id
                   AND cancelado_em IS NULL
                   AND consumido_em IS NULL
                   AND expira_em > now()
                """,
                new MapSqlParameterSource().addValue("id", contextoId),
                this::mapearContextoAtivo).stream().findFirst();
    }

    private Optional<UUID> localizarUsuarioProjetoPorEmail(final Long clienteEcossistemaId,
                                                           final String emailInformado) {
        if (clienteEcossistemaId == null || !StringUtils.hasText(emailInformado)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT u.id AS usuario_id
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
                 ORDER BY ufa.principal DESC, uce.atualizado_em DESC
                 LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("email", emailInformado.trim().toLowerCase(Locale.ROOT)),
                this::mapearUuid).stream().findFirst();
    }

    private MapSqlParameterSource parametrosBase(final UUID id,
                                                 final Long clienteEcossistemaId,
                                                 final String provedor,
                                                 final String identificadorExterno,
                                                 final String emailSocialNormalizado,
                                                 final String nomeUsuarioExterno,
                                                 final String nomeExibicaoExterno,
                                                 final String urlAvatarExterno,
                                                 final UUID perfilSistemaIdSugerido,
                                                 final String identificadorPublicoSistemaSugerido,
                                                 final String modoPendente,
                                                 final OffsetDateTime expiraEm,
                                                 final OffsetDateTime agora) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("provedor", provedor)
                .addValue("identificadorExterno", identificadorExterno)
                .addValue("emailSocialNormalizado", emailSocialNormalizado)
                .addValue("nomeUsuarioExterno", nomeUsuarioExterno)
                .addValue("nomeExibicaoExterno", nomeExibicaoExterno)
                .addValue("urlAvatarExterno", urlAvatarExterno)
                .addValue("perfilSistemaIdSugerido", perfilSistemaIdSugerido)
                .addValue("identificadorPublicoSistemaSugerido", identificadorPublicoSistemaSugerido)
                .addValue("modoPendente", modoPendente)
                .addValue("tentativasMaximas", TENTATIVAS_MAXIMAS_PADRAO)
                .addValue("expiraEm", expiraEm)
                .addValue("criadoEm", agora)
                .addValue("atualizadoEm", agora);
    }

    private UUID mapearUuid(final ResultSet rs, final int rowNum) throws SQLException {
        return rs.getObject("id", UUID.class);
    }

    private ContextoSocialPendenteAtivo mapearContextoAtivo(final ResultSet rs, final int rowNum)
            throws SQLException {
        return new ContextoSocialPendenteAtivo(
                rs.getObject("id", UUID.class),
                rs.getLong("cliente_ecossistema_id"),
                rs.getObject("usuario_id_sugerido", UUID.class),
                rs.getString("login_sugerido"),
                rs.getString("modo_pendente"),
                rs.getInt("tentativas_falhas"),
                rs.getInt("tentativas_maximas")
        );
    }

    private ContextoSocialPendenteCadastro mapearContextoCadastro(final ResultSet rs, final int rowNum)
            throws SQLException {
        return new ContextoSocialPendenteCadastro(
                rs.getObject("id", UUID.class),
                rs.getString("provedor"),
                rs.getString("identificador_externo"),
                rs.getString("nome_usuario_externo"),
                rs.getString("nome_exibicao_externo"),
                rs.getString("url_avatar_externo")
        );
    }

    private String normalizarObrigatorio(final String valor, final String campo) {
        Objects.requireNonNull(valor, campo + " é obrigatório");
        String texto = valor.trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return texto.toLowerCase(Locale.ROOT);
    }

    private String normalizarEmail(final String emailSocial) {
        if (!StringUtils.hasText(emailSocial)) {
            return "";
        }
        return emailSocial.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizarOpcional(final String valor) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        return valor.trim();
    }

    public record ContextoSocialPendenteAtivo(
            UUID id,
            Long clienteEcossistemaId,
            UUID perfilSistemaIdSugerido,
            String identificadorPublicoSistemaSugerido,
            String modoPendente,
            int tentativasFalhas,
            int tentativasMaximas
    ) {
        public boolean aceitaLogin(final String loginNormalizado) {
            if (!StringUtils.hasText(identificadorPublicoSistemaSugerido) || !StringUtils.hasText(loginNormalizado)) {
                return false;
            }
            return identificadorPublicoSistemaSugerido.trim().equalsIgnoreCase(loginNormalizado.trim());
        }

        public boolean modoEntrarEVincular() {
            return MODO_ENTRAR_E_VINCULAR.equalsIgnoreCase(modoPendente);
        }
    }

    public record ResultadoFalhaEntrarEVincular(
            boolean encontrado,
            int tentativasFalhas,
            int tentativasMaximas,
            boolean canceladoPorLimite
    ) {
        public static ResultadoFalhaEntrarEVincular inexistente() {
            return new ResultadoFalhaEntrarEVincular(false, 0, TENTATIVAS_MAXIMAS_PADRAO, false);
        }
    }

    public record ContextoSocialPendenteCadastro(
            UUID id,
            String provedor,
            String identificadorExterno,
            String nomeUsuarioExterno,
            String nomeExibicaoExterno,
            String urlAvatarExterno
    ) {
    }
}
