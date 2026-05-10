package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.DesafioAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusCadastroConta;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SincronizacaoModeloMultiappService {

    private static final String CLIENTE_PADRAO_CODIGO = "eickrono-thimisu-app";
    private static final String SISTEMA_PUBLICO_LEGADO = "app-flutter-publico";
    private static final String CLIENTE_PADRAO_NOME = "Eickrono Thimisu App";
    private static final String CLIENTE_PADRAO_TIPO = "APP_MOVEL";
    private static final String STATUS_USUARIO_ATIVO = "ATIVO";
    private static final String STATUS_VINCULO_ATIVO = "ATIVO";
    private static final String TIPO_FORMA_ACESSO_EMAIL_SENHA = "EMAIL_SENHA";
    private static final String PROVEDOR_EMAIL = "EMAIL";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HexFormat hexFormat = HexFormat.of();

    public SincronizacaoModeloMultiappService(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate é obrigatório");
    }

    public void sincronizarCadastro(final CadastroConta cadastroConta) {
        Objects.requireNonNull(cadastroConta, "cadastroConta é obrigatório");
        String codigoClienteEcossistema = resolverCodigoClienteEcossistema(cadastroConta.getSistemaSolicitante());
        String identificadorPublicoCliente = normalizarIdentificadorPublicoCliente(cadastroConta.getUsuario());
        Long clienteEcossistemaId = assegurarClienteEcossistema(codigoClienteEcossistema, cadastroConta.getAtualizadoEm());
        Long sistemaOrigemId = assegurarSistemaOrigem(cadastroConta.getSistemaSolicitante(), cadastroConta.getAtualizadoEm());
        UUID pessoaId = gerarPessoaId(cadastroConta.getSubjectRemoto());
        UUID usuarioId = null;

        if (cadastroConta.emailJaConfirmado()) {
            usuarioId = assegurarUsuarioAtivo(
                    cadastroConta.getSubjectRemoto(),
                    cadastroConta.getEmailPrincipal(),
                    identificadorPublicoCliente,
                    cadastroConta.getCriadoEm(),
                    cadastroConta.getAtualizadoEm(),
                    clienteEcossistemaId,
                    codigoClienteEcossistema,
                    cadastroConta.getEmailConfirmadoEm()
            );
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", cadastroConta.getCadastroId())
                .addValue("pessoaId", pessoaId)
                .addValue("usuarioId", usuarioId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("sistemaOrigemId", sistemaOrigemId)
                .addValue("emailId", gerarEmailId(cadastroConta.getEmailPrincipal()))
                .addValue("telefoneId", gerarTelefoneId(cadastroConta.getTelefonePrincipal()).orElse(null))
                .addValue("statusProcesso", mapearStatusCadastro(cadastroConta))
                .addValue("codigoEmailHash", cadastroConta.getCodigoEmailHash())
                .addValue("codigoEmailGeradoEm", cadastroConta.getCodigoEmailGeradoEm())
                .addValue("codigoEmailExpiraEm", cadastroConta.getCodigoEmailExpiraEm())
                .addValue("tentativasConfirmacaoEmail", cadastroConta.getTentativasConfirmacaoEmail())
                .addValue("reenviosEmail", cadastroConta.getReenviosEmail())
                .addValue("emailConfirmadoEm", cadastroConta.getEmailConfirmadoEm())
                .addValue("concluidoEm", cadastroConta.emailJaConfirmado() ? cadastroConta.getEmailConfirmadoEm() : null)
                .addValue("ipSolicitante", cadastroConta.getIpSolicitante())
                .addValue("userAgentSolicitante", cadastroConta.getUserAgentSolicitante())
                .addValue("criadoEm", cadastroConta.getCriadoEm())
                .addValue("atualizadoEm", cadastroConta.getAtualizadoEm());

        jdbcTemplate.update("""
                INSERT INTO autenticacao.cadastros_conta (
                    id,
                    pessoa_id,
                    usuario_id,
                    cliente_ecossistema_id,
                    sistema_origem_id,
                    email_id,
                    telefone_id,
                    status_processo,
                    codigo_email_hash,
                    codigo_email_gerado_em,
                    codigo_email_expira_em,
                    tentativas_confirmacao_email,
                    reenvios_email,
                    email_confirmado_em,
                    concluido_em,
                    ip_solicitante,
                    user_agent_solicitante,
                    criado_em,
                    atualizado_em
                )
                VALUES (
                    :id,
                    :pessoaId,
                    :usuarioId,
                    :clienteEcossistemaId,
                    :sistemaOrigemId,
                    :emailId,
                    :telefoneId,
                    :statusProcesso,
                    :codigoEmailHash,
                    :codigoEmailGeradoEm,
                    :codigoEmailExpiraEm,
                    :tentativasConfirmacaoEmail,
                    :reenviosEmail,
                    :emailConfirmadoEm,
                    :concluidoEm,
                    :ipSolicitante,
                    :userAgentSolicitante,
                    :criadoEm,
                    :atualizadoEm
                )
                ON CONFLICT (id) DO UPDATE
                SET pessoa_id = EXCLUDED.pessoa_id,
                    usuario_id = EXCLUDED.usuario_id,
                    cliente_ecossistema_id = EXCLUDED.cliente_ecossistema_id,
                    sistema_origem_id = EXCLUDED.sistema_origem_id,
                    email_id = EXCLUDED.email_id,
                    telefone_id = EXCLUDED.telefone_id,
                    status_processo = EXCLUDED.status_processo,
                    codigo_email_hash = EXCLUDED.codigo_email_hash,
                    codigo_email_gerado_em = EXCLUDED.codigo_email_gerado_em,
                    codigo_email_expira_em = EXCLUDED.codigo_email_expira_em,
                    tentativas_confirmacao_email = EXCLUDED.tentativas_confirmacao_email,
                    reenvios_email = EXCLUDED.reenvios_email,
                    email_confirmado_em = EXCLUDED.email_confirmado_em,
                    concluido_em = EXCLUDED.concluido_em,
                    ip_solicitante = EXCLUDED.ip_solicitante,
                    user_agent_solicitante = EXCLUDED.user_agent_solicitante,
                    atualizado_em = EXCLUDED.atualizado_em
                """, params);
    }

    public long assegurarClienteEcossistemaParaSistemaSolicitante(final String sistemaSolicitante,
                                                                  final OffsetDateTime momento) {
        return assegurarClienteEcossistema(sistemaSolicitante, momento);
    }

    public void removerCadastro(final UUID cadastroId) {
        if (cadastroId == null) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("cadastroId", cadastroId);
        jdbcTemplate.update("""
                UPDATE seguranca.atestacoes_app_desafios
                SET cadastro_id = NULL
                WHERE cadastro_id = :cadastroId
                """, params);
        jdbcTemplate.update("""
                DELETE FROM autenticacao.cadastros_conta
                WHERE id = :cadastroId
                """, params);
    }

    public void sincronizarRecuperacaoSenha(final RecuperacaoSenha recuperacaoSenha) {
        Objects.requireNonNull(recuperacaoSenha, "recuperacaoSenha é obrigatória");
        Long clienteEcossistemaId = assegurarClienteEcossistema(CLIENTE_PADRAO_CODIGO, recuperacaoSenha.getAtualizadoEm());
        UUID usuarioId = null;
        if (recuperacaoSenha.possuiDestinoReal()) {
            usuarioId = assegurarUsuarioAtivo(
                    recuperacaoSenha.getSubjectRemoto(),
                    recuperacaoSenha.getEmailPrincipal(),
                    null,
                    recuperacaoSenha.getCriadoEm(),
                    recuperacaoSenha.getAtualizadoEm(),
                    clienteEcossistemaId,
                    CLIENTE_PADRAO_CODIGO,
                    recuperacaoSenha.getSenhaRedefinidaEm()
            );
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", recuperacaoSenha.getFluxoId())
                .addValue("usuarioId", usuarioId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("emailId", gerarEmailId(recuperacaoSenha.getEmailPrincipal()))
                .addValue("statusProcesso", mapearStatusRecuperacao(recuperacaoSenha))
                .addValue("codigoEmailHash", recuperacaoSenha.getCodigoEmailHash())
                .addValue("codigoEmailGeradoEm", recuperacaoSenha.getCodigoEmailGeradoEm())
                .addValue("codigoEmailExpiraEm", recuperacaoSenha.getCodigoEmailExpiraEm())
                .addValue("tentativasConfirmacaoEmail", recuperacaoSenha.getTentativasConfirmacaoEmail())
                .addValue("reenviosEmail", recuperacaoSenha.getReenviosEmail())
                .addValue("codigoConfirmadoEm", recuperacaoSenha.getCodigoConfirmadoEm())
                .addValue("senhaRedefinidaEm", recuperacaoSenha.getSenhaRedefinidaEm())
                .addValue("criadoEm", recuperacaoSenha.getCriadoEm())
                .addValue("atualizadoEm", recuperacaoSenha.getAtualizadoEm());

        jdbcTemplate.update("""
                INSERT INTO autenticacao.recuperacoes_senha (
                    id,
                    usuario_id,
                    cliente_ecossistema_id,
                    email_id,
                    status_processo,
                    codigo_email_hash,
                    codigo_email_gerado_em,
                    codigo_email_expira_em,
                    tentativas_confirmacao_email,
                    reenvios_email,
                    codigo_confirmado_em,
                    senha_redefinida_em,
                    criado_em,
                    atualizado_em
                )
                VALUES (
                    :id,
                    :usuarioId,
                    :clienteEcossistemaId,
                    :emailId,
                    :statusProcesso,
                    :codigoEmailHash,
                    :codigoEmailGeradoEm,
                    :codigoEmailExpiraEm,
                    :tentativasConfirmacaoEmail,
                    :reenviosEmail,
                    :codigoConfirmadoEm,
                    :senhaRedefinidaEm,
                    :criadoEm,
                    :atualizadoEm
                )
                ON CONFLICT (id) DO UPDATE
                SET usuario_id = EXCLUDED.usuario_id,
                    cliente_ecossistema_id = EXCLUDED.cliente_ecossistema_id,
                    email_id = EXCLUDED.email_id,
                    status_processo = EXCLUDED.status_processo,
                    codigo_email_hash = EXCLUDED.codigo_email_hash,
                    codigo_email_gerado_em = EXCLUDED.codigo_email_gerado_em,
                    codigo_email_expira_em = EXCLUDED.codigo_email_expira_em,
                    tentativas_confirmacao_email = EXCLUDED.tentativas_confirmacao_email,
                    reenvios_email = EXCLUDED.reenvios_email,
                    codigo_confirmado_em = EXCLUDED.codigo_confirmado_em,
                    senha_redefinida_em = EXCLUDED.senha_redefinida_em,
                    atualizado_em = EXCLUDED.atualizado_em
                """, params);
    }

    public void sincronizarRegistroDispositivo(final RegistroDispositivo registro) {
        Objects.requireNonNull(registro, "registro é obrigatório");
        Long clienteEcossistemaId = assegurarClienteEcossistema(CLIENTE_PADRAO_CODIGO, registro.getCriadoEm());
        UUID usuarioId = registro.getUsuarioSub()
                .map(sub -> assegurarUsuarioAtivo(
                        sub,
                        registro.getEmail(),
                        null,
                        registro.getCriadoEm(),
                        registro.getConfirmadoEm().orElse(registro.getCriadoEm()),
                        clienteEcossistemaId,
                        CLIENTE_PADRAO_CODIGO,
                        registro.getConfirmadoEm().orElse(null)))
                .orElse(null);
        UUID pessoaId = registro.getUsuarioSub().map(this::gerarPessoaId).orElse(null);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", registro.getId())
                .addValue("usuarioId", usuarioId)
                .addValue("pessoaId", pessoaId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("sistemaOrigemId", null)
                .addValue("cadastroId", null)
                .addValue("emailId", gerarEmailId(registro.getEmail()))
                .addValue("telefoneId", registro.getTelefone().flatMap(this::gerarTelefoneId).orElse(null))
                .addValue("fingerprint", registro.getFingerprint())
                .addValue("plataforma", registro.getPlataforma())
                .addValue("versaoApp", registro.getVersaoAplicativo().orElse(null))
                .addValue("chavePublica", registro.getChavePublica().orElse(null))
                .addValue("status", registro.getStatus().name())
                .addValue("criadoEm", registro.getCriadoEm())
                .addValue("expiraEm", registro.getExpiraEm())
                .addValue("confirmadoEm", registro.getConfirmadoEm().orElse(null))
                .addValue("encerradoEm", registro.getCanceladoEm().orElse(null))
                .addValue("reenvios", registro.getReenvios());

        jdbcTemplate.update("""
                INSERT INTO dispositivos.registros_dispositivo (
                    id,
                    usuario_id,
                    pessoa_id,
                    cliente_ecossistema_id,
                    sistema_origem_id,
                    cadastro_id,
                    email_id,
                    telefone_id,
                    fingerprint,
                    plataforma,
                    versao_app,
                    chave_publica,
                    status,
                    criado_em,
                    expira_em,
                    confirmado_em,
                    encerrado_em,
                    reenvios
                )
                VALUES (
                    :id,
                    :usuarioId,
                    :pessoaId,
                    :clienteEcossistemaId,
                    :sistemaOrigemId,
                    :cadastroId,
                    :emailId,
                    :telefoneId,
                    :fingerprint,
                    :plataforma,
                    :versaoApp,
                    :chavePublica,
                    :status,
                    :criadoEm,
                    :expiraEm,
                    :confirmadoEm,
                    :encerradoEm,
                    :reenvios
                )
                ON CONFLICT (id) DO UPDATE
                SET usuario_id = EXCLUDED.usuario_id,
                    pessoa_id = EXCLUDED.pessoa_id,
                    cliente_ecossistema_id = EXCLUDED.cliente_ecossistema_id,
                    sistema_origem_id = EXCLUDED.sistema_origem_id,
                    cadastro_id = EXCLUDED.cadastro_id,
                    email_id = EXCLUDED.email_id,
                    telefone_id = EXCLUDED.telefone_id,
                    fingerprint = EXCLUDED.fingerprint,
                    plataforma = EXCLUDED.plataforma,
                    versao_app = EXCLUDED.versao_app,
                    chave_publica = EXCLUDED.chave_publica,
                    status = EXCLUDED.status,
                    expira_em = EXCLUDED.expira_em,
                    confirmado_em = EXCLUDED.confirmado_em,
                    encerrado_em = EXCLUDED.encerrado_em,
                    reenvios = EXCLUDED.reenvios
                """, params);

        registro.getCodigos().forEach(codigo -> sincronizarCodigoVerificacao(registro, codigo));
    }

    public void sincronizarDispositivoIdentidade(final DispositivoIdentidade dispositivo) {
        Objects.requireNonNull(dispositivo, "dispositivo é obrigatório");
        if (dispositivo.getId() == null) {
            return;
        }
        Long clienteEcossistemaId = assegurarClienteEcossistema(CLIENTE_PADRAO_CODIGO, dispositivo.getAtualizadoEm());
        UUID usuarioId = assegurarUsuarioAtivo(
                dispositivo.getUsuarioSub(),
                null,
                null,
                dispositivo.getCriadoEm(),
                dispositivo.getAtualizadoEm(),
                clienteEcossistemaId,
                CLIENTE_PADRAO_CODIGO,
                dispositivo.getUltimoTokenEmitidoEm().orElse(null)
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", gerarDispositivoConfiavelId(dispositivo.getId()))
                .addValue("usuarioId", usuarioId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("fingerprint", dispositivo.getFingerprint())
                .addValue("plataforma", dispositivo.getPlataforma())
                .addValue("versaoAppAtual", dispositivo.getVersaoAplicativo().orElse(null))
                .addValue("chavePublicaAtual", dispositivo.getChavePublica().orElse(null))
                .addValue("status", dispositivo.getStatus().name())
                .addValue("criadoEm", dispositivo.getCriadoEm())
                .addValue("atualizadoEm", dispositivo.getAtualizadoEm())
                .addValue("ultimoTokenEmitidoEm", dispositivo.getUltimoTokenEmitidoEm().orElse(null));

        jdbcTemplate.update("""
                INSERT INTO dispositivos.dispositivos_confiaveis (
                    id,
                    usuario_id,
                    cliente_ecossistema_id,
                    fingerprint,
                    plataforma,
                    versao_app_atual,
                    chave_publica_atual,
                    status,
                    criado_em,
                    atualizado_em,
                    ultimo_token_emitido_em
                )
                VALUES (
                    :id,
                    :usuarioId,
                    :clienteEcossistemaId,
                    :fingerprint,
                    :plataforma,
                    :versaoAppAtual,
                    :chavePublicaAtual,
                    :status,
                    :criadoEm,
                    :atualizadoEm,
                    :ultimoTokenEmitidoEm
                )
                ON CONFLICT (usuario_id, cliente_ecossistema_id, fingerprint) DO UPDATE
                SET plataforma = EXCLUDED.plataforma,
                    versao_app_atual = EXCLUDED.versao_app_atual,
                    chave_publica_atual = EXCLUDED.chave_publica_atual,
                    status = EXCLUDED.status,
                    atualizado_em = EXCLUDED.atualizado_em,
                    ultimo_token_emitido_em = EXCLUDED.ultimo_token_emitido_em
                """, params);
    }

    public void sincronizarTokenDispositivo(final TokenDispositivo token) {
        Objects.requireNonNull(token, "token é obrigatório");
        Long dispositivoLegacyId = token.getDispositivo().map(DispositivoIdentidade::getId).orElse(null);
        if (dispositivoLegacyId == null) {
            return;
        }
        String usuarioSub = token.getUsuarioSub();
        Long clienteEcossistemaId = assegurarClienteEcossistema(CLIENTE_PADRAO_CODIGO, token.getEmitidoEm());
        assegurarUsuarioAtivo(
                usuarioSub,
                token.getRegistro().getEmail(),
                null,
                token.getEmitidoEm(),
                token.getEmitidoEm(),
                clienteEcossistemaId,
                CLIENTE_PADRAO_CODIGO,
                token.getEmitidoEm()
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", token.getId())
                .addValue("dispositivoId", gerarDispositivoConfiavelId(dispositivoLegacyId))
                .addValue("registroDispositivoId", token.getRegistro().getId())
                .addValue("tokenHash", token.getTokenHash())
                .addValue("status", token.getStatus().name())
                .addValue("emitidoEm", token.getEmitidoEm())
                .addValue("expiraEm", token.getExpiraEm())
                .addValue("revogadoEm", token.getRevogadoEm().orElse(null))
                .addValue("motivoRevogacao", token.getMotivoRevogacao().map(Enum::name).orElse(null));

        jdbcTemplate.update("""
                INSERT INTO dispositivos.tokens_dispositivo (
                    id,
                    dispositivo_id,
                    registro_dispositivo_id,
                    token_hash,
                    status,
                    emitido_em,
                    expira_em,
                    revogado_em,
                    motivo_revogacao
                )
                VALUES (
                    :id,
                    :dispositivoId,
                    :registroDispositivoId,
                    :tokenHash,
                    :status,
                    :emitidoEm,
                    :expiraEm,
                    :revogadoEm,
                    :motivoRevogacao
                )
                ON CONFLICT (id) DO UPDATE
                SET dispositivo_id = EXCLUDED.dispositivo_id,
                    registro_dispositivo_id = EXCLUDED.registro_dispositivo_id,
                    token_hash = EXCLUDED.token_hash,
                    status = EXCLUDED.status,
                    emitido_em = EXCLUDED.emitido_em,
                    expira_em = EXCLUDED.expira_em,
                    revogado_em = EXCLUDED.revogado_em,
                    motivo_revogacao = EXCLUDED.motivo_revogacao
                """, params);
    }

    public void sincronizarDesafioAtestacao(final DesafioAtestacaoApp desafio) {
        Objects.requireNonNull(desafio, "desafio é obrigatório");
        Long clienteEcossistemaId = assegurarClienteEcossistema(CLIENTE_PADRAO_CODIGO, desafio.getCriadoEm());
        UUID usuarioId = null;
        UUID pessoaId = null;
        UUID vinculoClienteId = null;

        if (desafio.getUsuarioSub() != null && !desafio.getUsuarioSub().isBlank()) {
            usuarioId = assegurarUsuarioAtivo(
                    desafio.getUsuarioSub(),
                    null,
                    null,
                    desafio.getCriadoEm(),
                    desafio.getConsumidoEm() != null ? desafio.getConsumidoEm() : desafio.getCriadoEm(),
                    clienteEcossistemaId,
                    CLIENTE_PADRAO_CODIGO,
                    null
            );
            pessoaId = gerarPessoaId(desafio.getUsuarioSub());
            vinculoClienteId = gerarVinculoClienteId(desafio.getUsuarioSub(), CLIENTE_PADRAO_CODIGO);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", gerarDesafioId(desafio.getIdentificadorDesafio()))
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("sistemaOrigemId", null)
                .addValue("usuarioId", usuarioId)
                .addValue("pessoaId", pessoaId)
                .addValue("vinculoClienteId", vinculoClienteId)
                .addValue("cadastroId", cadastroExiste(desafio.getCadastroId()) ? desafio.getCadastroId() : null)
                .addValue("registroDispositivoId",
                        registroExiste(desafio.getRegistroDispositivoId()) ? desafio.getRegistroDispositivoId() : null)
                .addValue("dispositivoId", null)
                .addValue("operacao", desafio.getOperacao().name())
                .addValue("plataforma", desafio.getPlataforma().name())
                .addValue("provedorEsperado", desafio.getProvedorEsperado().name())
                .addValue("desafioBase64", desafio.getDesafioBase64())
                .addValue("ipSolicitante", desafio.getIpSolicitante())
                .addValue("userAgentSolicitante", desafio.getUserAgentSolicitante())
                .addValue("criadoEm", desafio.getCriadoEm())
                .addValue("expiraEm", desafio.getExpiraEm())
                .addValue("consumidoEm", desafio.getConsumidoEm());

        jdbcTemplate.update("""
                INSERT INTO seguranca.atestacoes_app_desafios (
                    id,
                    cliente_ecossistema_id,
                    sistema_origem_id,
                    usuario_id,
                    pessoa_id,
                    vinculo_cliente_id,
                    cadastro_id,
                    registro_dispositivo_id,
                    dispositivo_id,
                    operacao,
                    plataforma,
                    provedor_esperado,
                    desafio_base64,
                    ip_solicitante,
                    user_agent_solicitante,
                    criado_em,
                    expira_em,
                    consumido_em
                )
                VALUES (
                    :id,
                    :clienteEcossistemaId,
                    :sistemaOrigemId,
                    :usuarioId,
                    :pessoaId,
                    :vinculoClienteId,
                    :cadastroId,
                    :registroDispositivoId,
                    :dispositivoId,
                    :operacao,
                    :plataforma,
                    :provedorEsperado,
                    :desafioBase64,
                    :ipSolicitante,
                    :userAgentSolicitante,
                    :criadoEm,
                    :expiraEm,
                    :consumidoEm
                )
                ON CONFLICT (id) DO UPDATE
                SET usuario_id = EXCLUDED.usuario_id,
                    pessoa_id = EXCLUDED.pessoa_id,
                    vinculo_cliente_id = EXCLUDED.vinculo_cliente_id,
                    cadastro_id = EXCLUDED.cadastro_id,
                    registro_dispositivo_id = EXCLUDED.registro_dispositivo_id,
                    dispositivo_id = EXCLUDED.dispositivo_id,
                    operacao = EXCLUDED.operacao,
                    plataforma = EXCLUDED.plataforma,
                    provedor_esperado = EXCLUDED.provedor_esperado,
                    desafio_base64 = EXCLUDED.desafio_base64,
                    ip_solicitante = EXCLUDED.ip_solicitante,
                    user_agent_solicitante = EXCLUDED.user_agent_solicitante,
                    expira_em = EXCLUDED.expira_em,
                    consumido_em = EXCLUDED.consumido_em
                """, params);
    }

    private void sincronizarCodigoVerificacao(final RegistroDispositivo registro, final CodigoVerificacao codigo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", codigo.getId())
                .addValue("registroDispositivoId", registro.getId())
                .addValue("canal", codigo.getCanal().name())
                .addValue("emailId", codigo.getCanal() == CanalVerificacao.EMAIL
                        ? gerarEmailId(codigo.getDestino())
                        : null)
                .addValue("telefoneId", codigo.getCanal() == CanalVerificacao.SMS
                        ? gerarTelefoneId(codigo.getDestino()).orElse(null)
                        : null)
                .addValue("codigoHash", codigo.getCodigoHash())
                .addValue("tentativas", codigo.getTentativas())
                .addValue("tentativasMaximas", codigo.getTentativasMaximas())
                .addValue("reenvios", codigo.getReenvios())
                .addValue("reenviosMaximos", codigo.getReenviosMaximos())
                .addValue("status", codigo.getStatus().name())
                .addValue("enviadoEm", codigo.getEnviadoEm().orElse(null))
                .addValue("confirmadoEm", codigo.getConfirmadoEm().orElse(null))
                .addValue("expiraEm", codigo.getExpiraEm());

        jdbcTemplate.update("""
                INSERT INTO dispositivos.codigos_verificacao_dispositivo (
                    id,
                    registro_dispositivo_id,
                    canal,
                    email_id,
                    telefone_id,
                    codigo_hash,
                    tentativas,
                    tentativas_maximas,
                    reenvios,
                    reenvios_maximos,
                    status,
                    enviado_em,
                    confirmado_em,
                    expira_em
                )
                VALUES (
                    :id,
                    :registroDispositivoId,
                    :canal,
                    :emailId,
                    :telefoneId,
                    :codigoHash,
                    :tentativas,
                    :tentativasMaximas,
                    :reenvios,
                    :reenviosMaximos,
                    :status,
                    :enviadoEm,
                    :confirmadoEm,
                    :expiraEm
                )
                ON CONFLICT (id) DO UPDATE
                SET email_id = EXCLUDED.email_id,
                    telefone_id = EXCLUDED.telefone_id,
                    codigo_hash = EXCLUDED.codigo_hash,
                    tentativas = EXCLUDED.tentativas,
                    tentativas_maximas = EXCLUDED.tentativas_maximas,
                    reenvios = EXCLUDED.reenvios,
                    reenvios_maximos = EXCLUDED.reenvios_maximos,
                    status = EXCLUDED.status,
                    enviado_em = EXCLUDED.enviado_em,
                    confirmado_em = EXCLUDED.confirmado_em,
                    expira_em = EXCLUDED.expira_em
                """, params);
    }

    private UUID assegurarUsuarioAtivo(final String subRemoto,
                                       final String emailPrincipal,
                                       final String identificadorPublicoCliente,
                                       final OffsetDateTime criadoEm,
                                       final OffsetDateTime atualizadoEm,
                                       final Long clienteEcossistemaId,
                                       final String codigoClienteEcossistema,
                                       final OffsetDateTime ultimoAcessoEm) {
        if (subRemoto == null || subRemoto.isBlank()) {
            return null;
        }

        UUID usuarioId = gerarUsuarioId(subRemoto);
        UUID pessoaId = gerarPessoaId(subRemoto);
        MapSqlParameterSource paramsUsuario = new MapSqlParameterSource()
                .addValue("id", usuarioId)
                .addValue("pessoaId", pessoaId)
                .addValue("subRemoto", subRemoto)
                .addValue("statusGlobal", STATUS_USUARIO_ATIVO)
                .addValue("credencialLocalHabilitada", true)
                .addValue("ultimoLoginEm", null)
                .addValue("criadoEm", criadoEm)
                .addValue("atualizadoEm", atualizadoEm);

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
                    :credencialLocalHabilitada,
                    :ultimoLoginEm,
                    :criadoEm,
                    :atualizadoEm
                )
                ON CONFLICT (sub_remoto) DO UPDATE
                SET pessoa_id = EXCLUDED.pessoa_id,
                    status_global = EXCLUDED.status_global,
                    credencial_local_habilitada = EXCLUDED.credencial_local_habilitada,
                    atualizado_em = EXCLUDED.atualizado_em
                """, paramsUsuario);

        assegurarVinculoUsuario(
                usuarioId,
                clienteEcossistemaId,
                codigoClienteEcossistema,
                normalizarIdentificadorPublicoCliente(identificadorPublicoCliente),
                criadoEm,
                atualizadoEm,
                ultimoAcessoEm,
                subRemoto
        );
        if (emailPrincipal != null && !emailPrincipal.isBlank()) {
            assegurarFormaAcessoEmail(usuarioId, emailPrincipal, criadoEm, atualizadoEm);
        }
        return usuarioId;
    }

    private void assegurarVinculoUsuario(final UUID usuarioId,
                                         final Long clienteEcossistemaId,
                                         final String codigoClienteEcossistema,
                                         final String identificadorPublicoCliente,
                                         final OffsetDateTime criadoEm,
                                         final OffsetDateTime atualizadoEm,
                                         final OffsetDateTime ultimoAcessoEm,
                                         final String subRemoto) {
        MapSqlParameterSource paramsVinculo = new MapSqlParameterSource()
                .addValue("id", gerarVinculoClienteId(subRemoto, codigoClienteEcossistema))
                .addValue("usuarioId", usuarioId)
                .addValue("clienteEcossistemaId", clienteEcossistemaId)
                .addValue("statusVinculo", STATUS_VINCULO_ATIVO)
                .addValue("identificadorPublicoCliente", identificadorPublicoCliente)
                .addValue("ultimoAcessoEm", ultimoAcessoEm)
                .addValue("vinculadoEm", criadoEm)
                .addValue("atualizadoEm", atualizadoEm)
                .addValue("revogadoEm", null)
                .addValue("motivoRevogacao", null);

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
                    :identificadorPublicoCliente,
                    :ultimoAcessoEm,
                    :vinculadoEm,
                    :atualizadoEm,
                    :revogadoEm,
                    :motivoRevogacao
                )
                ON CONFLICT (usuario_id, cliente_ecossistema_id) DO UPDATE
                SET status_vinculo = EXCLUDED.status_vinculo,
                    identificador_publico_cliente = COALESCE(
                        EXCLUDED.identificador_publico_cliente,
                        autenticacao.usuarios_clientes_ecossistema.identificador_publico_cliente
                    ),
                    ultimo_acesso_em = COALESCE(EXCLUDED.ultimo_acesso_em,
                                                autenticacao.usuarios_clientes_ecossistema.ultimo_acesso_em),
                    atualizado_em = EXCLUDED.atualizado_em,
                    revogado_em = NULL,
                    motivo_revogacao = NULL
                """, paramsVinculo);
    }

    private String normalizarIdentificadorPublicoCliente(final String identificadorPublicoCliente) {
        if (identificadorPublicoCliente == null) {
            return null;
        }
        String normalizado = identificadorPublicoCliente.trim().toLowerCase(Locale.ROOT);
        return normalizado.isBlank() ? null : normalizado;
    }

    private void assegurarFormaAcessoEmail(final UUID usuarioId,
                                           final String emailPrincipal,
                                           final OffsetDateTime criadoEm,
                                           final OffsetDateTime atualizadoEm) {
        String emailNormalizado = emailPrincipal.trim().toLowerCase(Locale.ROOT);
        MapSqlParameterSource paramsForma = new MapSqlParameterSource()
                .addValue("id", gerarFormaAcessoId(TIPO_FORMA_ACESSO_EMAIL_SENHA, PROVEDOR_EMAIL, emailNormalizado))
                .addValue("usuarioId", usuarioId)
                .addValue("emailId", gerarEmailId(emailNormalizado))
                .addValue("tipo", TIPO_FORMA_ACESSO_EMAIL_SENHA)
                .addValue("provedor", PROVEDOR_EMAIL)
                .addValue("identificadorExterno", emailNormalizado)
                .addValue("principal", true)
                .addValue("verificadoEm", atualizadoEm)
                .addValue("vinculadoEm", criadoEm)
                .addValue("desvinculadoEm", null);

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
                    :principal,
                    :verificadoEm,
                    :vinculadoEm,
                    :desvinculadoEm
                )
                ON CONFLICT (tipo, provedor, identificador_externo) DO UPDATE
                SET usuario_id = EXCLUDED.usuario_id,
                    email_id = EXCLUDED.email_id,
                    principal = EXCLUDED.principal,
                    verificado_em = EXCLUDED.verificado_em,
                    desvinculado_em = NULL
                """, paramsForma);
    }

    private Long assegurarClienteEcossistema(final String codigoClienteEcossistema, final OffsetDateTime momento) {
        OffsetDateTime instante = Objects.requireNonNullElseGet(momento, OffsetDateTime::now);
        String codigoCliente = resolverCodigoClienteEcossistema(codigoClienteEcossistema);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("codigo", codigoCliente)
                .addValue("nome", resolverNomeClienteEcossistema(codigoCliente))
                .addValue("tipo", resolverTipoClienteEcossistema(codigoCliente))
                .addValue("agora", instante);
        jdbcTemplate.update("""
                INSERT INTO catalogo.clientes_ecossistema (
                    codigo,
                    nome,
                    tipo,
                    client_id_oidc,
                    ativo,
                    criado_em,
                    atualizado_em
                )
                VALUES (
                    :codigo,
                    :nome,
                    :tipo,
                    NULL,
                    TRUE,
                    :agora,
                    :agora
                )
                ON CONFLICT (codigo) DO UPDATE
                SET nome = EXCLUDED.nome,
                    tipo = EXCLUDED.tipo,
                    ativo = TRUE,
                    atualizado_em = EXCLUDED.atualizado_em
                """, params);
        return buscarIdLong("""
                SELECT id
                FROM catalogo.clientes_ecossistema
                WHERE codigo = :codigo
                """, params).orElseThrow(() -> new IllegalStateException("Cliente do ecossistema não encontrado."));
    }

    private Long assegurarSistemaOrigem(final String identificadorSistema, final OffsetDateTime momento) {
        if (identificadorSistema == null || identificadorSistema.isBlank()) {
            return null;
        }
        OffsetDateTime instante = Objects.requireNonNullElseGet(momento, OffsetDateTime::now);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("identificadorSistema", identificadorSistema.trim())
                .addValue("descricao", "Origem técnica observada durante o cutover do modelo multiapp")
                .addValue("agora", instante);
        jdbcTemplate.update("""
                INSERT INTO catalogo.sistemas_origem (
                    identificador_sistema,
                    descricao,
                    ativo,
                    criado_em,
                    atualizado_em
                )
                VALUES (
                    :identificadorSistema,
                    :descricao,
                    TRUE,
                    :agora,
                    :agora
                )
                ON CONFLICT (identificador_sistema) DO UPDATE
                SET descricao = COALESCE(catalogo.sistemas_origem.descricao, EXCLUDED.descricao),
                    ativo = TRUE,
                    atualizado_em = EXCLUDED.atualizado_em
                """, params);
        return buscarIdLong("""
                SELECT id
                FROM catalogo.sistemas_origem
                WHERE identificador_sistema = :identificadorSistema
                """, params).orElse(null);
    }

    private Optional<Long> buscarIdLong(final String sql, final MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, params, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty());
    }

    private boolean cadastroExiste(final UUID cadastroId) {
        if (cadastroId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                    FROM autenticacao.cadastros_conta
                    WHERE id = :id
                )
                """, new MapSqlParameterSource("id", cadastroId), Boolean.class));
    }

    private boolean registroExiste(final UUID registroId) {
        if (registroId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                    FROM dispositivos.registros_dispositivo
                    WHERE id = :id
                )
                """, new MapSqlParameterSource("id", registroId), Boolean.class));
    }

    private String mapearStatusCadastro(final CadastroConta cadastroConta) {
        return cadastroConta.getStatus() == StatusCadastroConta.EMAIL_CONFIRMADO ? "CONCLUIDO" : "ABERTO";
    }

    private String mapearStatusRecuperacao(final RecuperacaoSenha recuperacaoSenha) {
        if (recuperacaoSenha.senhaJaRedefinida()) {
            return "SENHA_REDEFINIDA";
        }
        if (recuperacaoSenha.codigoJaConfirmado()) {
            return "EMAIL_CONFIRMADO";
        }
        return "ABERTO";
    }

    private UUID gerarUsuarioId(final String subRemoto) {
        return gerarUuidDeterministico("autenticacao.usuario:" + subRemoto);
    }

    private UUID gerarPessoaId(final String subRemoto) {
        return gerarUuidDeterministico("identidade.pessoa:" + subRemoto);
    }

    private UUID gerarEmailId(final String email) {
        return gerarUuidDeterministico("identidade.email:" + email.trim().toLowerCase(Locale.ROOT));
    }

    private Optional<UUID> gerarTelefoneId(final String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(gerarUuidDeterministico("identidade.telefone:" + telefone.trim()));
    }

    private String resolverCodigoClienteEcossistema(final String identificadorSistema) {
        if (identificadorSistema == null || identificadorSistema.isBlank()) {
            return CLIENTE_PADRAO_CODIGO;
        }
        String codigoNormalizado = identificadorSistema.trim();
        if (SISTEMA_PUBLICO_LEGADO.equalsIgnoreCase(codigoNormalizado)
                || CLIENTE_PADRAO_CODIGO.equalsIgnoreCase(codigoNormalizado)) {
            return CLIENTE_PADRAO_CODIGO;
        }
        return codigoNormalizado;
    }

    private String resolverNomeClienteEcossistema(final String codigoClienteEcossistema) {
        if (CLIENTE_PADRAO_CODIGO.equalsIgnoreCase(codigoClienteEcossistema)) {
            return CLIENTE_PADRAO_NOME;
        }
        return codigoClienteEcossistema;
    }

    private String resolverTipoClienteEcossistema(final String codigoClienteEcossistema) {
        return CLIENTE_PADRAO_TIPO;
    }

    private UUID gerarVinculoClienteId(final String subRemoto, final String codigoClienteEcossistema) {
        return gerarUuidDeterministico("autenticacao.vinculo_cliente:" + subRemoto + ":" + codigoClienteEcossistema);
    }

    private UUID gerarFormaAcessoId(final String tipo, final String provedor, final String identificadorExterno) {
        return gerarUuidDeterministico("autenticacao.forma_acesso:" + tipo + ":" + provedor + ":" + identificadorExterno);
    }

    private UUID gerarDispositivoConfiavelId(final Long legacyDispositivoId) {
        return gerarUuidDeterministico("dispositivos.confiavel:" + legacyDispositivoId);
    }

    private UUID gerarDesafioId(final String identificadorDesafio) {
        return gerarUuidDeterministico("seguranca.desafio:" + identificadorDesafio);
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
}
