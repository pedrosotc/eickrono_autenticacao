package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoCodigoRecuperacaoSenhaRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.RecuperacaoSenhaIniciada;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import com.eickrono.api.identidade.dominio.repositorio.RecuperacaoSenhaRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RecuperacaoSenhaService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RecuperacaoSenhaRepositorio recuperacaoSenhaRepositorio;
    private final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;
    private final CanalEnvioCodigoRecuperacaoSenhaEmail canalEnvioCodigoRecuperacaoSenhaEmail;
    private final DispositivoProperties dispositivoProperties;
    private final Clock clock;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;
    private final HexFormat hexFormat = HexFormat.of();

    @Autowired
    public RecuperacaoSenhaService(final RecuperacaoSenhaRepositorio recuperacaoSenhaRepositorio,
                                   final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                   final CanalEnvioCodigoRecuperacaoSenhaEmail canalEnvioCodigoRecuperacaoSenhaEmail,
                                   final DispositivoProperties dispositivoProperties,
                                   final Clock clock,
                                   final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService) {
        this.recuperacaoSenhaRepositorio = Objects.requireNonNull(
                recuperacaoSenhaRepositorio, "recuperacaoSenhaRepositorio é obrigatório");
        this.clienteAdministracaoCadastroKeycloak = Objects.requireNonNull(
                clienteAdministracaoCadastroKeycloak, "clienteAdministracaoCadastroKeycloak é obrigatório");
        this.canalEnvioCodigoRecuperacaoSenhaEmail = Objects.requireNonNull(
                canalEnvioCodigoRecuperacaoSenhaEmail, "canalEnvioCodigoRecuperacaoSenhaEmail é obrigatório");
        this.dispositivoProperties = Objects.requireNonNull(dispositivoProperties, "dispositivoProperties é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.sincronizacaoModeloMultiappService = sincronizacaoModeloMultiappService;
    }

    public RecuperacaoSenhaService(final RecuperacaoSenhaRepositorio recuperacaoSenhaRepositorio,
                                   final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                   final CanalEnvioCodigoRecuperacaoSenhaEmail canalEnvioCodigoRecuperacaoSenhaEmail,
                                   final DispositivoProperties dispositivoProperties,
                                   final Clock clock) {
        this(
                recuperacaoSenhaRepositorio,
                clienteAdministracaoCadastroKeycloak,
                canalEnvioCodigoRecuperacaoSenhaEmail,
                dispositivoProperties,
                clock,
                null
        );
    }

    public RecuperacaoSenhaIniciada iniciar(final String emailPrincipal) {
        String emailNormalizado = obrigatorio(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        OffsetDateTime agora = OffsetDateTime.now(clock);
        Optional<UsuarioCadastroKeycloakExistente> usuarioExistente =
                clienteAdministracaoCadastroKeycloak.buscarUsuarioPorEmail(emailNormalizado);
        String codigoClaro = gerarCodigoNumerico();
        String materialHash = usuarioExistente.map(UsuarioCadastroKeycloakExistente::subjectRemoto)
                .filter(valor -> !valor.isBlank())
                .orElse("email-desconhecido:" + UUID.randomUUID());

        RecuperacaoSenha recuperacaoSenha = recuperacaoSenhaRepositorio.save(new RecuperacaoSenha(
                UUID.randomUUID(),
                usuarioExistente.map(UsuarioCadastroKeycloakExistente::subjectRemoto).orElse(null),
                emailNormalizado,
                hashCodigoEmail(codigoClaro, emailNormalizado, materialHash),
                agora,
                agora.plusHours(dispositivoProperties.getCodigo().getExpiracaoHoras()),
                agora,
                agora
        ));

        sincronizarRecuperacaoSeConfigurado(recuperacaoSenha);
        if (usuarioExistente.isPresent()) {
            canalEnvioCodigoRecuperacaoSenhaEmail.enviar(recuperacaoSenha, codigoClaro);
        }

        return new RecuperacaoSenhaIniciada(recuperacaoSenha.getFluxoId());
    }

    public ConfirmacaoCodigoRecuperacaoSenhaRealizada confirmarCodigo(final UUID fluxoId, final String codigo) {
        RecuperacaoSenha recuperacaoSenha = obterRecuperacao(fluxoId);
        if (recuperacaoSenha.senhaJaRedefinida()) {
            return new ConfirmacaoCodigoRecuperacaoSenhaRealizada(fluxoId, true, false);
        }
        if (recuperacaoSenha.codigoJaConfirmado()) {
            return new ConfirmacaoCodigoRecuperacaoSenhaRealizada(fluxoId, true, true);
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (recuperacaoSenha.codigoExpirado(agora)) {
            throw new ResponseStatusException(HttpStatus.GONE, "O código de recuperação expirou.");
        }
        if (recuperacaoSenha.getTentativasConfirmacaoEmail() >= dispositivoProperties.getCodigo().getTentativasMaximas()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "O limite de tentativas de recuperação foi atingido."
            );
        }

        recuperacaoSenha.registrarTentativaConfirmacao(agora);
        String codigoNormalizado = obrigatorio(codigo, "codigo");
        if (!recuperacaoSenha.possuiDestinoReal()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O código de recuperação informado é inválido.");
        }
        if (!Objects.equals(
                recuperacaoSenha.getCodigoEmailHash(),
                hashCodigoEmail(
                        codigoNormalizado,
                        recuperacaoSenha.getEmailPrincipal(),
                        recuperacaoSenha.getSubjectRemoto()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O código de recuperação informado é inválido.");
        }

        recuperacaoSenha.marcarCodigoConfirmado(agora);
        sincronizarRecuperacaoSeConfigurado(recuperacaoSenha);
        return new ConfirmacaoCodigoRecuperacaoSenhaRealizada(fluxoId, true, true);
    }

    public void reenviarCodigo(final UUID fluxoId) {
        RecuperacaoSenha recuperacaoSenha = obterRecuperacao(fluxoId);
        if (recuperacaoSenha.senhaJaRedefinida() || recuperacaoSenha.codigoJaConfirmado()) {
            return;
        }
        if (recuperacaoSenha.ultrapassouReenviosEmail(dispositivoProperties.getCodigo().getReenviosMaximos())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "O limite de reenvios foi atingido.");
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        String codigoClaro = gerarCodigoNumerico();
        String materialHash = recuperacaoSenha.possuiDestinoReal()
                ? recuperacaoSenha.getSubjectRemoto()
                : "email-desconhecido:" + UUID.randomUUID();
        recuperacaoSenha.atualizarCodigoEmail(
                hashCodigoEmail(codigoClaro, recuperacaoSenha.getEmailPrincipal(), materialHash),
                agora,
                agora.plusHours(dispositivoProperties.getCodigo().getExpiracaoHoras()),
                agora
        );
        sincronizarRecuperacaoSeConfigurado(recuperacaoSenha);
        if (recuperacaoSenha.possuiDestinoReal()) {
            canalEnvioCodigoRecuperacaoSenhaEmail.enviar(recuperacaoSenha, codigoClaro);
        }
    }

    public void redefinirSenha(final UUID fluxoId, final String senhaPura, final String confirmacaoSenha) {
        RecuperacaoSenha recuperacaoSenha = obterRecuperacao(fluxoId);
        if (!recuperacaoSenha.codigoJaConfirmado()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A recuperação de senha ainda não foi validada por código."
            );
        }
        if (recuperacaoSenha.senhaJaRedefinida()) {
            return;
        }

        String senhaNormalizada = obrigatorio(senhaPura, "senha");
        String confirmacaoNormalizada = obrigatorio(confirmacaoSenha, "confirmacaoSenha");
        if (!Objects.equals(senhaNormalizada, confirmacaoNormalizada)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A confirmação de senha não confere.");
        }
        validarPoliticaSenha(senhaNormalizada);

        if (!recuperacaoSenha.possuiDestinoReal()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível redefinir a senha informada.");
        }

        clienteAdministracaoCadastroKeycloak.redefinirSenha(recuperacaoSenha.getSubjectRemoto(), senhaNormalizada);
        recuperacaoSenha.marcarSenhaRedefinida(OffsetDateTime.now(clock));
        sincronizarRecuperacaoSeConfigurado(recuperacaoSenha);
    }

    public static void validarPoliticaSenha(final String senhaPura) {
        String senha = obrigatorio(senhaPura, "senha");
        boolean possuiMaiuscula = senha.chars().anyMatch(Character::isUpperCase);
        boolean possuiMinuscula = senha.chars().anyMatch(Character::isLowerCase);
        boolean possuiNumero = senha.chars().anyMatch(Character::isDigit);
        boolean possuiEspecial = senha.chars().anyMatch(caractere -> !Character.isLetterOrDigit(caractere));
        if (senha.length() < 8 || !possuiMaiuscula || !possuiMinuscula || !possuiNumero || !possuiEspecial) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula, uma letra minúscula, um número e um caractere especial."
            );
        }
    }

    private RecuperacaoSenha obterRecuperacao(final UUID fluxoId) {
        if (fluxoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fluxoId é obrigatório.");
        }
        return recuperacaoSenhaRepositorio.findByFluxoId(fluxoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fluxo de recuperação não encontrado."));
    }

    private String gerarCodigoNumerico() {
        int limite = (int) Math.pow(10, dispositivoProperties.getCodigo().getTamanho());
        String formato = "%0" + dispositivoProperties.getCodigo().getTamanho() + "d";
        return formato.formatted(SECURE_RANDOM.nextInt(limite));
    }

    private String hashCodigoEmail(final String codigoClaro,
                                   final String emailPrincipal,
                                   final String subjectRemoto) {
        String material = obrigatorio(codigoClaro, "codigoClaro")
                + "|" + obrigatorio(emailPrincipal, "emailPrincipal")
                + "|" + obrigatorio(subjectRemoto, "subjectRemoto")
                + "|RECUPERACAO_SENHA";
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(
                    dispositivoProperties.getCodigo().getSegredoHmac().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALG
            ));
            return hexFormat.formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao calcular o hash do código de recuperação de senha.", ex);
        }
    }

    private static String obrigatorio(final String valor, final String campo) {
        String normalizado = Objects.requireNonNull(valor, campo + " é obrigatório").trim();
        if (normalizado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " é obrigatório.");
        }
        return normalizado;
    }

    private void sincronizarRecuperacaoSeConfigurado(final RecuperacaoSenha recuperacaoSenha) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarRecuperacaoSenha(recuperacaoSenha);
        }
    }
}
