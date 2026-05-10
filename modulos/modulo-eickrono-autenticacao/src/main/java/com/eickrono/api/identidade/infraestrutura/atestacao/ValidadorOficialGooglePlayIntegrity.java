package com.eickrono.api.identidade.infraestrutura.atestacao;

import com.eickrono.api.identidade.aplicacao.excecao.AtestacaoAppInvalidaException;
import com.eickrono.api.identidade.aplicacao.modelo.ComprovanteAtestacaoAppEntrada;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoLocalAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.servico.ValidadorOficialAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Validação oficial de tokens Android usando o endpoint recomendado do Google Play Integrity.
 */
@Component
@ConditionalOnProperty(prefix = "identidade.atestacao.app.google", name = "habilitado", havingValue = "true")
public class ValidadorOficialGooglePlayIntegrity implements ValidadorOficialAtestacaoApp {

    private static final String ESCOPO_PLAY_INTEGRITY = "https://www.googleapis.com/auth/playintegrity";

    private final GooglePlayIntegrityProperties properties;
    private final RestClient restClient;

    public ValidadorOficialGooglePlayIntegrity(final com.eickrono.api.identidade.infraestrutura.configuracao.AtestacaoAppProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório").getGoogle();
        this.restClient = RestClient.create();
    }

    @Override
    public boolean suporta(final PlataformaAtestacaoApp plataforma) {
        return plataforma == PlataformaAtestacaoApp.ANDROID;
    }

    @Override
    public ValidacaoOficialAtestacaoAppResultado validar(final ComprovanteAtestacaoAppEntrada comprovante,
                                                         final ValidacaoLocalAtestacaoAppResultado validacaoLocal) {
        validarConfiguracao();
        DecodificarIntegridadeGoogleResponse resposta = decodificarToken(comprovante.conteudoComprovante());
        PayloadIntegridadeGoogle payload = Objects.requireNonNull(
                resposta.tokenPayloadExternal(),
                "A resposta do Google Play Integrity não trouxe tokenPayloadExternal."
        );
        validarRequestDetails(payload.requestDetails(), comprovante.desafioBase64());
        validarAppIntegrity(payload.appIntegrity());
        validarDeviceIntegrity(payload.deviceIntegrity());
        validarLicenciamento(payload.accountDetails());

        List<String> deviceVerdicts = payload.deviceIntegrity() != null
                && payload.deviceIntegrity().deviceRecognitionVerdict() != null
                ? payload.deviceIntegrity().deviceRecognitionVerdict()
                : List.of();

        return new ValidacaoOficialAtestacaoAppResultado(
                true,
                "Validacao oficial Google Play Integrity concluida com sucesso.",
                payload.appIntegrity() != null ? payload.appIntegrity().appRecognitionVerdict() : null,
                payload.accountDetails() != null ? payload.accountDetails().appLicensingVerdict() : null,
                deviceVerdicts
        );
    }

    private DecodificarIntegridadeGoogleResponse decodificarToken(final String integrityToken) {
        try {
            return restClient.post()
                    .uri("https://playintegrity.googleapis.com/v1/{packageName}:decodeIntegrityToken",
                            properties.getPackageName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + obterAccessToken())
                    .body(new DecodificarIntegridadeGoogleRequest(integrityToken))
                    .retrieve()
                    .body(DecodificarIntegridadeGoogleResponse.class);
        } catch (RestClientException ex) {
            throw new AtestacaoAppInvalidaException(
                    "falha_validacao_google",
                    "Nao foi possivel validar o token no Google Play Integrity."
            );
        }
    }

    private String obterAccessToken() {
        Path caminhoCredencial = Path.of(properties.getServiceAccountJsonArquivo());
        try (InputStream inputStream = Files.newInputStream(caminhoCredencial)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(List.of(ESCOPO_PLAY_INTEGRITY));
            credentials.refreshIfExpired();
            AccessToken accessToken = credentials.getAccessToken();
            if (accessToken == null || accessToken.getTokenValue() == null || accessToken.getTokenValue().isBlank()) {
                throw new AtestacaoAppInvalidaException(
                        "token_google_ausente",
                        "Nao foi possivel obter um access token valido para o Google Play Integrity."
                );
            }
            return accessToken.getTokenValue();
        } catch (IOException ex) {
            throw new AtestacaoAppInvalidaException(
                    "credencial_google_invalida",
                    "Nao foi possivel carregar a credencial de servico do Google Play Integrity."
            );
        }
    }

    private void validarRequestDetails(final RequestDetails requestDetails, final String desafioEsperado) {
        if (requestDetails == null) {
            throw new AtestacaoAppInvalidaException(
                    "request_details_ausente",
                    "A resposta oficial do Google nao trouxe requestDetails."
            );
        }
        if (!properties.getPackageName().equals(requestDetails.requestPackageName())) {
            throw new AtestacaoAppInvalidaException(
                    "package_name_divergente",
                    "O package name validado pelo Google nao corresponde ao app esperado."
            );
        }
        if (!desafioEsperado.equals(requestDetails.nonce())) {
            throw new AtestacaoAppInvalidaException(
                    "nonce_divergente",
                    "O nonce validado pelo Google nao corresponde ao desafio emitido."
            );
        }
        long diferencaMillis = Instant.now().toEpochMilli() - requestDetails.timestampMillis();
        if (diferencaMillis > properties.getToleranciaTempoToken().toMillis()) {
            throw new AtestacaoAppInvalidaException(
                    "token_expirado_google",
                    "O token do Google Play Integrity ultrapassou a janela de tolerancia configurada."
            );
        }
    }

    private void validarAppIntegrity(final AppIntegrity appIntegrity) {
        if (properties.isExigirPlayRecognized()) {
            String verdict = appIntegrity != null ? appIntegrity.appRecognitionVerdict() : null;
            if (!"PLAY_RECOGNIZED".equals(verdict)) {
                throw new AtestacaoAppInvalidaException(
                        "app_nao_reconhecido_google",
                        "O Google Play Integrity nao reconheceu o app como distribuido pela Play Store."
                );
            }
        }
    }

    private void validarDeviceIntegrity(final DeviceIntegrity deviceIntegrity) {
        if (properties.isExigirDeviceIntegrity()) {
            List<String> verdicts = deviceIntegrity != null ? deviceIntegrity.deviceRecognitionVerdict() : List.of();
            if (verdicts == null || verdicts.stream().noneMatch("MEETS_DEVICE_INTEGRITY"::equals)) {
                throw new AtestacaoAppInvalidaException(
                        "device_integrity_insuficiente",
                        "O Google Play Integrity nao confirmou o nivel minimo de integridade do dispositivo."
                );
            }
        }
    }

    private void validarLicenciamento(final AccountDetails accountDetails) {
        if (properties.isExigirLicenciado()) {
            String verdict = accountDetails != null ? accountDetails.appLicensingVerdict() : null;
            if (!"LICENSED".equals(verdict)) {
                throw new AtestacaoAppInvalidaException(
                        "app_nao_licenciado",
                        "O Google Play Integrity nao confirmou o licenciamento do app para a conta atual."
                );
            }
        }
    }

    private void validarConfiguracao() {
        if (properties.getPackageName() == null || properties.getPackageName().isBlank()) {
            throw new AtestacaoAppInvalidaException(
                    "package_name_google_obrigatorio",
                    "A configuracao do package name do Google Play Integrity e obrigatoria."
            );
        }
        if (properties.getServiceAccountJsonArquivo() == null || properties.getServiceAccountJsonArquivo().isBlank()) {
            throw new AtestacaoAppInvalidaException(
                    "credencial_google_obrigatoria",
                    "O caminho da credencial de servico do Google Play Integrity e obrigatorio."
            );
        }
    }

    private record DecodificarIntegridadeGoogleRequest(
            @JsonProperty("integrity_token") String integrityToken
    ) {
    }

    private record DecodificarIntegridadeGoogleResponse(
            PayloadIntegridadeGoogle tokenPayloadExternal
    ) {
    }

    private record PayloadIntegridadeGoogle(
            RequestDetails requestDetails,
            AppIntegrity appIntegrity,
            DeviceIntegrity deviceIntegrity,
            AccountDetails accountDetails
    ) {
    }

    private record RequestDetails(
            String requestPackageName,
            String nonce,
            long timestampMillis
    ) {
    }

    private record AppIntegrity(
            String appRecognitionVerdict
    ) {
    }

    private record DeviceIntegrity(
            List<String> deviceRecognitionVerdict
    ) {
    }

    private record AccountDetails(
            String appLicensingVerdict
    ) {
    }
}
