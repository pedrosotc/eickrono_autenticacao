package com.eickrono.api.identidade.infraestrutura.atestacao;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.eickrono.api.identidade.aplicacao.excecao.AtestacaoAppInvalidaException;
import com.eickrono.api.identidade.aplicacao.modelo.ComprovanteAtestacaoAppEntrada;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoLocalAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.servico.ValidadorOficialAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ChaveAppleAppAttest;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.repositorio.ChaveAppleAppAttestRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.AtestacaoAppProperties;
import com.webauthn4j.anchor.TrustAnchorRepository;
import com.webauthn4j.appattest.DeviceCheckManager;
import com.webauthn4j.appattest.authenticator.DCAppleDevice;
import com.webauthn4j.appattest.authenticator.DCAppleDeviceImpl;
import com.webauthn4j.appattest.data.DCAssertionData;
import com.webauthn4j.appattest.data.DCAssertionParameters;
import com.webauthn4j.appattest.data.DCAssertionRequest;
import com.webauthn4j.appattest.data.DCAttestationData;
import com.webauthn4j.appattest.data.DCAttestationParameters;
import com.webauthn4j.appattest.data.DCAttestationRequest;
import com.webauthn4j.appattest.data.attestation.statement.AppleAppAttestAttestationStatement;
import com.webauthn4j.appattest.server.DCServerProperty;
import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.AuthenticatorDataConverter;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.util.CertificateUtil;
import com.webauthn4j.util.MessageDigestUtil;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier;
import com.webauthn4j.verifier.exception.VerificationException;

import jakarta.transaction.Transactional;

@Component
@Transactional
@ConditionalOnProperty(prefix = "identidade.atestacao.app.apple", name = "habilitado", havingValue = "true")
public final class ValidadorOficialAppleAppAttest implements ValidadorOficialAtestacaoApp {

    private final AppleAppAttestProperties properties;
    private final ChaveAppleAppAttestRepositorio chaveRepositorio;
    private final DeviceCheckManager deviceCheckManager;
    private final ObjectConverter objectConverter;
    private final AttestationObjectConverter attestationObjectConverter;
    private final AuthenticatorDataConverter authenticatorDataConverter;
    private final AttestedCredentialDataConverter attestedCredentialDataConverter;

    public ValidadorOficialAppleAppAttest(final AtestacaoAppProperties properties,
                                          final ChaveAppleAppAttestRepositorio chaveRepositorio,
                                          final ResourceLoader resourceLoader) {
        this.properties = Objects.requireNonNull(
                Objects.requireNonNull(properties, "properties é obrigatório").getApple(),
                "apple properties é obrigatório");
        this.chaveRepositorio = Objects.requireNonNull(chaveRepositorio, "chaveRepositorio é obrigatório");
        this.objectConverter = DeviceCheckManager.createObjectConverter();
        this.attestationObjectConverter = new AttestationObjectConverter(this.objectConverter);
        this.authenticatorDataConverter = new AuthenticatorDataConverter(this.objectConverter);
        this.attestedCredentialDataConverter = new AttestedCredentialDataConverter(this.objectConverter);
        this.deviceCheckManager = new DeviceCheckManager(
                new DefaultCertPathTrustworthinessVerifier(criarTrustAnchorRepository(resourceLoader))
        );
    }

    @Override
    public boolean suporta(final PlataformaAtestacaoApp plataforma) {
        return plataforma == PlataformaAtestacaoApp.IOS;
    }

    @Override
    public ValidacaoOficialAtestacaoAppResultado validar(final ComprovanteAtestacaoAppEntrada comprovante,
                                                         final ValidacaoLocalAtestacaoAppResultado validacaoLocal) {
        validarConfiguracao();
        try {
            return switch (Objects.requireNonNull(comprovante.tipoComprovante(), "tipoComprovante é obrigatório")) {
                case OBJETO_ATESTACAO -> validarObjetoAtestacao(comprovante, validacaoLocal);
                case OBJETO_ASSERCAO -> validarObjetoAssercao(comprovante, validacaoLocal);
                default -> throw new AtestacaoAppInvalidaException(
                        "tipo_comprovante_ios_invalido",
                        "O tipo de comprovante informado nao e valido para a validacao oficial iOS."
                );
            };
        } catch (DataConversionException | VerificationException ex) {
            throw new AtestacaoAppInvalidaException(
                    "falha_validacao_apple_app_attest",
                    "Nao foi possivel validar oficialmente o comprovante do Apple App Attest."
            );
        } catch (IOException ex) {
            throw new AtestacaoAppInvalidaException(
                    "falha_leitura_apple_app_attest",
                    "Nao foi possivel reconstruir o registro atestado do Apple App Attest."
            );
        }
    }

    private ValidacaoOficialAtestacaoAppResultado validarObjetoAtestacao(final ComprovanteAtestacaoAppEntrada comprovante,
                                                                         final ValidacaoLocalAtestacaoAppResultado validacaoLocal)
            throws DataConversionException, VerificationException {
        Objects.requireNonNull(validacaoLocal, "validacaoLocal é obrigatória");
        byte[] chaveId = decodificarChaveId(comprovante.chaveId());
        byte[] clientDataHash = calcularHashCliente(comprovante);
        byte[] objetoAtestacao = decodificarBase64Padrao(
                comprovante.conteudoComprovante(),
                "conteudo_atestacao_ios_invalido",
                "O objeto de atestacao do Apple App Attest esta invalido."
        );
        DCServerProperty serverProperty = criarServerProperty(comprovante);
        DCAttestationData attestationData = Objects.requireNonNull(
                deviceCheckManager.validate(
                        new DCAttestationRequest(chaveId, objetoAtestacao, clientDataHash),
                        new DCAttestationParameters(serverProperty)
                ),
                "Validação oficial Apple retornou atestação nula.");
        var attestationObject = attestationData.getAttestationObject();
        if (attestationObject == null) {
            throw new IllegalStateException("Validação oficial Apple retornou attestation object nulo.");
        }
        var authenticatorData = attestationObject.getAuthenticatorData();
        if (authenticatorData == null) {
            throw new IllegalStateException("Validação oficial Apple retornou authenticator data nulo.");
        }
        long contador = authenticatorData.getSignCount();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<ChaveAppleAppAttest> chaveExistente = chaveRepositorio.findByChaveId(comprovante.chaveId());
        ChaveAppleAppAttest chave = chaveExistente.orElseGet(() -> new ChaveAppleAppAttest(
                comprovante.chaveId(),
                comprovante.conteudoComprovante(),
                contador,
                agora,
                agora
        ));
        chave.atualizarRegistroAtestacao(comprovante.conteudoComprovante(), contador, agora);
        chaveRepositorio.save(chave);

        return new ValidacaoOficialAtestacaoAppResultado(
                true,
                "Validacao oficial Apple App Attest concluida com sucesso.",
                properties.getBundleIdentifier(),
                null,
                List.of("APPLE_APP_ATTEST_OK")
        );
    }

    private ValidacaoOficialAtestacaoAppResultado validarObjetoAssercao(final ComprovanteAtestacaoAppEntrada comprovante,
                                                                        final ValidacaoLocalAtestacaoAppResultado validacaoLocal)
            throws DataConversionException, VerificationException, IOException {
        Objects.requireNonNull(validacaoLocal, "validacaoLocal é obrigatória");
        ChaveAppleAppAttest chave = chaveRepositorio.findByChaveId(comprovante.chaveId())
                .orElseThrow(() -> new AtestacaoAppInvalidaException(
                        "chave_ios_nao_encontrada",
                        "Nenhum registro oficial do Apple App Attest foi encontrado para a chave informada."
                ));
        byte[] chaveId = decodificarChaveId(comprovante.chaveId());
        byte[] clientDataHash = calcularHashCliente(comprovante);
        byte[] objetoAssercao = decodificarBase64Padrao(
                comprovante.conteudoComprovante(),
                "conteudo_assercao_ios_invalido",
                "O objeto de assercao do Apple App Attest esta invalido."
        );
        DCAssertionData assertionData = Objects.requireNonNull(
                deviceCheckManager.validate(
                        new DCAssertionRequest(chaveId, objetoAssercao, clientDataHash),
                        new DCAssertionParameters(criarServerProperty(comprovante), reconstruirDispositivo(chave))
                ),
                "Validação oficial Apple retornou asserção nula.");
        var authenticatorData = assertionData.getAuthenticatorData();
        if (authenticatorData == null) {
            throw new IllegalStateException("Validação oficial Apple retornou authenticator data nulo.");
        }

        chave.atualizarContadorAssinatura(
                authenticatorData.getSignCount(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        chaveRepositorio.save(chave);

        return new ValidacaoOficialAtestacaoAppResultado(
                true,
                "Validacao oficial Apple App Attest concluida com sucesso.",
                properties.getBundleIdentifier(),
                null,
                List.of("APPLE_APP_ASSERTION_OK")
        );
    }

    private DCAppleDevice reconstruirDispositivo(final ChaveAppleAppAttest chave) throws IOException {
        byte[] objetoAtestacao = decodificarBase64Padrao(
                chave.getObjetoAtestacaoBase64(),
                "objeto_atestacao_ios_persistido_invalido",
                "O registro persistido do Apple App Attest esta invalido."
        );
        byte[] authenticatorDataBytes = attestationObjectConverter.extractAuthenticatorData(objetoAtestacao);
        if (authenticatorDataBytes == null) {
            throw new IllegalStateException("Authenticator data extraído do Apple App Attest não pode ser nulo.");
        }
        AuthenticatorData<AuthenticationExtensionAuthenticatorOutput> authenticatorData =
                Objects.requireNonNull(
                        authenticatorDataConverter.convert(authenticatorDataBytes),
                        "Authenticator data convertido do Apple App Attest não pode ser nulo.");
        byte[] attestedCredentialDataBytes =
                Objects.requireNonNull(
                        authenticatorDataConverter.extractAttestedCredentialData(authenticatorDataBytes),
                        "Attested credential data do Apple App Attest não pode ser nulo.");
        AttestedCredentialData attestedCredentialData =
                Objects.requireNonNull(
                        attestedCredentialDataConverter.convert(attestedCredentialDataBytes),
                        "Attested credential data convertido do Apple App Attest não pode ser nulo.");
        byte[] attestationStatementBytes = attestationObjectConverter.extractAttestationStatement(objetoAtestacao);
        if (attestationStatementBytes == null) {
            throw new IllegalStateException("Attestation statement do Apple App Attest não pode ser nulo.");
        }
        AppleAppAttestAttestationStatement attestationStatement =
                Objects.requireNonNull(
                        objectConverter.getCborMapper().readValue(
                                attestationStatementBytes,
                                AppleAppAttestAttestationStatement.class
                        ),
                        "Attestation statement convertido do Apple App Attest não pode ser nulo.");
        AuthenticationExtensionsAuthenticatorOutputs<?> extensoes = authenticatorData.getExtensions() == null
                ? new AuthenticationExtensionsAuthenticatorOutputs<>()
                : authenticatorData.getExtensions();
        return new DCAppleDeviceImpl(
                attestedCredentialData,
                attestationStatement,
                chave.getContadorAssinatura(),
                castExtensoes(extensoes)
        );
    }

    @SuppressWarnings("unchecked")
    private AuthenticationExtensionsAuthenticatorOutputs<com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput>
    castExtensoes(final AuthenticationExtensionsAuthenticatorOutputs<?> extensoes) {
        return (AuthenticationExtensionsAuthenticatorOutputs<com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput>) extensoes;
    }

    private byte[] calcularHashCliente(final ComprovanteAtestacaoAppEntrada comprovante) {
        byte[] desafio = decodificarBase64Url(
                Objects.requireNonNull(comprovante, "comprovante é obrigatório").desafioBase64(),
                "desafio_ios_invalido",
                "O desafio emitido para o Apple App Attest esta invalido."
        );
        return MessageDigestUtil.createSHA256().digest(desafio);
    }

    private byte[] decodificarBase64Url(final String valor,
                                        final String codigoErro,
                                        final String mensagemErro) {
        try {
            String normalizado = Objects.requireNonNull(valor, "valor é obrigatório")
                    .trim()
                    .replace('-', '+')
                    .replace('_', '/');
            int padding = normalizado.length() % 4;
            if (padding != 0) {
                normalizado = normalizado + "=".repeat(4 - padding);
            }
            return Base64.getDecoder().decode(normalizado);
        } catch (IllegalArgumentException ex) {
            throw new AtestacaoAppInvalidaException(codigoErro, mensagemErro);
        }
    }

    private byte[] decodificarBase64Padrao(final String valor,
                                           final String codigoErro,
                                           final String mensagemErro) {
        try {
            return Base64.getDecoder().decode(Objects.requireNonNull(valor, "valor é obrigatório").trim());
        } catch (IllegalArgumentException ex) {
            throw new AtestacaoAppInvalidaException(codigoErro, mensagemErro);
        }
    }

    private byte[] decodificarChaveId(final String chaveId) {
        if (chaveId == null || chaveId.isBlank()) {
            throw new AtestacaoAppInvalidaException(
                    "chave_ios_obrigatoria",
                    "A chave do Apple App Attest e obrigatoria para a validacao oficial."
            );
        }
        try {
            return Base64.getDecoder().decode(chaveId.trim());
        } catch (IllegalArgumentException ex) {
            try {
                return decodificarBase64Url(
                        chaveId,
                        "chave_ios_invalida",
                        "A chave do Apple App Attest esta invalida."
                );
            } catch (AtestacaoAppInvalidaException ignored) {
                throw new AtestacaoAppInvalidaException(
                        "chave_ios_invalida",
                        "A chave do Apple App Attest esta invalida."
                );
            }
        }
    }

    private DCServerProperty criarServerProperty(final ComprovanteAtestacaoAppEntrada comprovante) {
        byte[] desafio = decodificarBase64Url(
                Objects.requireNonNull(comprovante, "comprovante é obrigatório").desafioBase64(),
                "desafio_ios_invalido",
                "O desafio emitido para o Apple App Attest esta invalido."
        );
        return new DCServerProperty(
                properties.getTeamIdentifier(),
                properties.getBundleIdentifier(),
                new DefaultChallenge(desafio)
        );
    }

    private void validarConfiguracao() {
        if (properties.getTeamIdentifier() == null || properties.getTeamIdentifier().isBlank()) {
            throw new AtestacaoAppInvalidaException(
                    "team_identifier_apple_obrigatorio",
                    "A configuracao do team identifier do Apple App Attest e obrigatoria."
            );
        }
        if (properties.getBundleIdentifier() == null || properties.getBundleIdentifier().isBlank()) {
            throw new AtestacaoAppInvalidaException(
                    "bundle_identifier_apple_obrigatorio",
                    "A configuracao do bundle identifier do Apple App Attest e obrigatoria."
            );
        }
    }

    private TrustAnchorRepository criarTrustAnchorRepository(final ResourceLoader resourceLoader) {
        X509Certificate certificate = carregarCertificadoRaiz(resourceLoader);
        return new TrustAnchorRepository() {
            @Override
            public Set<TrustAnchor> find(final AAGUID aaguid) {
                return criarTrustAnchors(certificate);
            }

            @Override
            public Set<TrustAnchor> find(final byte[] attestationCertificateKeyIdentifier) {
                return criarTrustAnchors(certificate);
            }
        };
    }

    private X509Certificate carregarCertificadoRaiz(final ResourceLoader resourceLoader) {
        try (InputStream inputStream = abrirRecurso(resourceLoader).getInputStream()) {
            return CertificateUtil.generateX509Certificate(inputStream);
        } catch (IOException ex) {
            throw new UncheckedIOException("Nao foi possivel carregar o certificado raiz do Apple App Attest.", ex);
        }
    }

    private Resource abrirRecurso(final ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(properties.getRootCaArquivo());
        if (!resource.exists()) {
            throw new IllegalStateException("Certificado raiz do Apple App Attest nao encontrado: " + properties.getRootCaArquivo());
        }
        return resource;
    }

    private Set<TrustAnchor> criarTrustAnchors(final X509Certificate certificate) {
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        trustAnchors.add(new TrustAnchor(certificate, null));
        return trustAnchors;
    }
}
