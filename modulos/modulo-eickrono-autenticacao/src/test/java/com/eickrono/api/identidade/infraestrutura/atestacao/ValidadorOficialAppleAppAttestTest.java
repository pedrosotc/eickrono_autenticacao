package com.eickrono.api.identidade.infraestrutura.atestacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.ComprovanteAtestacaoAppEntrada;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoLocalAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado;
import com.eickrono.api.identidade.dominio.modelo.ChaveAppleAppAttest;
import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.TipoComprovanteAtestacaoApp;
import com.eickrono.api.identidade.dominio.repositorio.ChaveAppleAppAttestRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.AtestacaoAppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.core.io.DefaultResourceLoader;

class ValidadorOficialAppleAppAttestTest {

    private static final String TEAM_IDENTIFIER = "8YE23NZS57";
    private static final String BUNDLE_IDENTIFIER = "com.kayak.travel";
    private static final String CHAVE_ID = "VnfqjSp0rWyyqNhrfh+9/IhLIvXuYTPAmJEVQwl4dko=";
    private static final byte[] DESAFIO_BYTES = "1234567890abcdefgh".getBytes(StandardCharsets.UTF_8);
    private static final String DESAFIO_BASE64 = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(DESAFIO_BYTES);
    private static final OffsetDateTime GERADO_EM = OffsetDateTime.parse("2020-09-28T20:28:20Z");
    private static final String OBJETO_ATESTACAO_BASE64 = """
            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
            NzdlYThkMmE3NGFkNmNiMmE4ZDg2YjdlMWZiZGZjODg0YjIyZjVlZTYxMzNjMDk4OTExNTQzMDk3ODc2NGExGjAYBgNVBAsMEUFB
            QSBDZXJ0aWZpY2F0aW9uMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMFkwEwYHKoZIzj0CAQYIKoZI
            zj0DAQcDQgAElTF3wUNp9YREYMn9dd5VhrLSFxyG3oiyYvqCGMmY/2qjRjwAiVkpkSv7+Qeu7mdm2azTUqEa8CcqDYsHq0b5naOB
            6jCB5zAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIE8DB1BgkqhkiG92NkCAUEaDBmpAMCAQq/iTADAgEBv4kxAwIBAL+JMgMC
            AQG/iTMDAgEBv4k0HQQbOFlFMjNOWlM1Ny5jb20ua2F5YWsudHJhdmVspQYEBHNrcyC/iTYDAgEFv4k3AwIBAL+JOQMCAQC/iToD
            AgEAMBsGCSqGSIb3Y2QIBwQOMAy/ingIBAYxNC4wLjEwMwYJKoZIhvdjZAgCBCYwJKEiBCDJr5gUtdOggggNJfgtiIXImCrUpyqf
            tahpr+fexKslfTAKBggqhkjOPQQDAgNoADBlAjEAtsVdgPnvY/eoqYQeht1Zud38zMPpWfy5YCgB72cuit4DQp8jHr/Pzu/U8Ihx
            cQanAjA8J3gazez9kRLRVXT43RhUqiUNQEtlSbV99VUR2c2OxTUS6skz6pLhfcZ2sujVpWMwKAIBBAIBAQQgvdrOOJAgFiv8POwN
            ggQqju68c8sP3Pm1C94DpHYynWYwYAIBBQIBAQRYK2VZNFNTbk9qZGlrK1hpM2lCUytTa0dWU0dNODZpSnlQU2FjK251MXVPeHdm
            b1RBS214OFNjdDNYckJqK3p2L3BPZFVKaHcyejdxNkg4R3pvL3pCbXc9PTAOAgEGAgEBBAZBVFRFU1QwEgIBBwIBAQQKcHJvZHVj
            dGlvbjAgAgEMAgEBBBgyMDIwLTA5LTI4VDIwOjI4OjE5BCcuOTQyWjAgAgEVAgEBBBgyMDIwLTEyLTI3VDIwOjI4OjE5Ljk0MloA
            AAAAAACggDCCA60wggNUoAMCAQICEFkzVq3lWYLPREI3rN9FG1MwCgYIKoZIzj0EAwIwfDEwMC4GA1UEAwwnQXBwbGUgQXBwbGlj
            YXRpb24gSW50ZWdyYXRpb24gQ0EgNSAtIEcxMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTETMBEGA1UE
            CgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMjAwNTE5MTc0NzMxWhcNMjEwNjE4MTc0NzMxWjBaMTYwNAYDVQQDDC1BcHBs
            aWNhdGlvbiBBdHRlc3RhdGlvbiBGcmF1ZCBSZWNlaXB0IFNpZ25pbmcxEzARBgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVT
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf+kVNGzDinuYPJPR0ENf2KvaVnAE0yxYhmVRlXq0ePfLKvi6Rff6eOrGLEnk+c3A
            hLUDFPECM9qbdvpEKiu4cqOCAdgwggHUMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAU2Rf+S2eQOEuS9NvO1VeAFAuPPckwQwYI
            KwYBBQUHAQEENzA1MDMGCCsGAQUFBzABhidodHRwOi8vb2NzcC5hcHBsZS5jb20vb2NzcDAzLWFhaWNhNWcxMDEwggEcBgNVHSAE
            ggETMIIBDzCCAQsGCSqGSIb3Y2QFATCB/TCBwwYIKwYBBQUHAgIwgbYMgbNSZWxpYW5jZSBvbiB0aGlzIGNlcnRpZmljYXRlIGJ5
            IGFueSBwYXJ0eSBhc3N1bWVzIGFjY2VwdGFuY2Ugb2YgdGhlIHRoZW4gYXBwbGljYWJsZSBzdGFuZGFyZCB0ZXJtcyBhbmQgY29u
            ZGl0aW9ucyBvZiB1c2UsIGNlcnRpZmljYXRlIHBvbGljeSBhbmQgY2VydGlmaWNhdGlvbiBwcmFjdGljZSBzdGF0ZW1lbnRzLjA1
            BggrBgEFBQcCARYpaHR0cDovL3d3dy5hcHBsZS5jb20vY2VydGlmaWNhdGVhdXRob3JpdHkwHQYDVR0OBBYEFGkexw9H7OON3XU3
            RPPp4VpsEFYlMA4GA1UdDwEB/wQEAwIHgDAPBgkqhkiG92NkDA8EAgUAMAoGCCqGSM49BAMCA0cAMEQCICUYFlxeKZxZ9oU5rV3b
            mfY3PvYOzQhFqf13GtYkLSwiAiBdKpsqX6ujY4FljRhA969IC9droZTYNCCH9NaTW7UbrjCCAvkwggJ/oAMCAQICEFb7g9Qr/43D
            N5kjtVqubr0wCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZp
            Y2F0aW9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTkwMzIyMTc1MzMzWhcNMzQwMzIy
            MDAwMDAwWjB8MTAwLgYDVQQDDCdBcHBsZSBBcHBsaWNhdGlvbiBJbnRlZ3JhdGlvbiBDQSA1IC0gRzExJjAkBgNVBAsMHUFwcGxl
            IENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqG
            SM49AwEHA0IABJLOY719hrGrKAo7HOGv+wSUgJGs9jHfpssoNW9ES+Eh5VfdEo2NuoJ8lb5J+r4zyq7NBBnxL0Ml+vS+s8uDfrqj
            gfcwgfQwDwYDVR0TAQH/BAUwAwEB/zAfBgNVHSMEGDAWgBS7sN6hWDOImqSKmd6+veuv2sskqzBGBggrBgEFBQcBAQQ6MDgwNgYI
            KwYBBQUHMAGGKmh0dHA6Ly9vY3NwLmFwcGxlLmNvbS9vY3NwMDMtYXBwbGVyb290Y2FnMzA3BgNVHR8EMDAuMCygKqAohiZodHRw
            Oi8vY3JsLmFwcGxlLmNvbS9hcHBsZXJvb3RjYWczLmNybDAdBgNVHQ4EFgQU2Rf+S2eQOEuS9NvO1VeAFAuPPckwDgYDVR0PAQH/
            BAQDAgEGMBAGCiqGSIb3Y2QGAgMEAgUAMAoGCCqGSM49BAMDA2gAMGUCMQCNb6afoeDk7FtOc4qSfz14U5iP9NofWB7DdUr+OKhM
            KoMaGqoNpmRt4bmT6NFVTO0CMGc7LLTh6DcHd8vV7HaoGjpVOz81asjF5pKw4WG+gElp5F8rqWzhEQKqzGHZOLdzSjCCAkMwggHJ
            oAMCAQICCC3F/IjSxUuVMAoGCCqGSM49BAMDMGcxGzAZBgNVBAMMEkFwcGxlIFJvb3QgQ0EgLSBHMzEmMCQGA1UECwwdQXBwbGUg
            Q2VydGlmaWNhdGlvbiBBdXRob3JpdHkxEzARBgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVTMB4XDTE0MDQzMDE4MTkwNloX
            DTM5MDQzMDE4MTkwNlowZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9u
            IEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAASY6S89QHKk
            7ZMicoETHN0QlfHFo05x3BQW2Q7lpgUqd2R7X04407scRLV/9R+2MmJdyemEW08wTxFaAP1YWAyl9Q8sTQdHE3Xal5eXbzFc7Sud
            eyA72LlU2V6ZpDpRCjGjQjBAMB0GA1UdDgQWBBS7sN6hWDOImqSKmd6+veuv2sskqzAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB
            /wQEAwIBBjAKBggqhkjOPQQDAwNoADBlAjEAg+nBxBZeGl00GNnt7/RsDgBGS7jfskYRxQ/95nqMoaZrzsID1Jz1k8Z0uGrfqiMV
            AjBtZooQytQN1E/NjUM+tIpjpTNu423aF7dkH8hTJvmIYnQ5Cxdby1GoDOgYA+eisigAADGCAZYwggGSAgEBMIGQMHwxMDAuBgNV
            BAMMJ0FwcGxlIEFwcGxpY2F0aW9uIEludGVncmF0aW9uIENBIDUgLSBHMTEmMCQGA1UECwwdQXBwbGUgQ2VydGlmaWNhdGlvbiBB
            dXRob3JpdHkxEzARBgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVTAhBZM1at5VmCz0RCN6zfRRtTMA0GCWCGSAFlAwQCAQUA
            oIGVMBgGCSqGSIb3DQEJAzELBgkqhkiG9w0BBwEwHAYJKoZIhvcNAQkFMQ8XDTIwMDkyODIwMjgyMFowKgYJKoZIhvcNAQk0MR0w
            GzANBglghkgBZQMEAgEFAKEKBggqhkjOPQQDAjAvBgkqhkiG9w0BCQQxIgQgyxRZaHevu9mf1wZLftRoPcHNW4p0ILAjKWeQNRnu
            H54wCgYIKoZIzj0EAwIERzBFAiEAhOOiqKJXPxbi9vfzFCtQLqrdl1CTytgw/WgyYGzzygcCIG7IIKLbIp//Y9cv2eKQXaWAhOvh
            WO8wkyKfyGlFsprWAAAAAAAAaGF1dGhEYXRhWKQwYALKBV4GxYilLsaqVIL1No4CrzCHsenTCdBAyvXZWkAAAAAAYXBwYXR0ZXN0
            AAAAAAAAAAAgVnfqjSp0rWyyqNhrfh+9/IhLIvXuYTPAmJEVQwl4dkqlAQIDJiABIVgglTF3wUNp9YREYMn9dd5VhrLSFxyG3oiy
            YvqCGMmY/2oiWCCjRjwAiVkpkSv7+Qeu7mdm2azTUqEa8CcqDYsHq0b5nQ==
            """
            .replace("\n", "")
            .strip();
    private static final String OBJETO_ASSERCAO_BASE64 = """
            omlzaWduYXR1cmVYRjBEAiB4zR/olM8j24vmT3fGVA1eykitnp/jpMG9sM6CNsF2lQIgVCK5x6m/jkocPmX6wuPqlJ8tzbvI9bQn
            d8XYebJ8XuBxYXV0aGVudGljYXRvckRhdGFYJTBgAsoFXgbFiKUuxqpUgvU2jgKvMIex6dMJ0EDK9dlaQAAAAAE=
            """
            .replace("\n", "")
            .strip();

    @Test
    void deveValidarAtestacaoEAssercaoDoAppleAppAttest() {
        AtomicReference<ChaveAppleAppAttest> persistido = new AtomicReference<>();
        ChaveAppleAppAttestRepositorio repositorio = mock(ChaveAppleAppAttestRepositorio.class);
        when(repositorio.findByChaveId(CHAVE_ID)).thenAnswer(invocacao -> Optional.ofNullable(persistido.get()));
        when(repositorio.save(any(ChaveAppleAppAttest.class))).thenAnswer(invocacao -> {
            ChaveAppleAppAttest chave = invocacao.getArgument(0);
            persistido.set(chave);
            return chave;
        });

        ValidadorOficialAppleAppAttest validador = new ValidadorOficialAppleAppAttest(
                criarProperties(),
                repositorio,
                new DefaultResourceLoader()
        );
        ValidacaoLocalAtestacaoAppResultado validacaoLocal = new ValidacaoLocalAtestacaoAppResultado(
                "desafio-123",
                OperacaoAtestacaoApp.CADASTRO,
                PlataformaAtestacaoApp.IOS,
                ProvedorAtestacaoApp.APPLE_APP_ATTEST,
                GERADO_EM.minusMinutes(1),
                GERADO_EM.plusMinutes(4),
                GERADO_EM,
                CHAVE_ID
        );
        Instant agoraFixo = Instant.parse("2020-09-28T00:00:00Z");

        try (MockedStatic<Instant> instant = mockStatic(Instant.class, Answers.CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(agoraFixo);

            ValidacaoOficialAtestacaoAppResultado resultadoAtestacao = validador.validar(
                    criarComprovante(TipoComprovanteAtestacaoApp.OBJETO_ATESTACAO, OBJETO_ATESTACAO_BASE64),
                    validacaoLocal
            );

            assertThat(resultadoAtestacao.executada()).isTrue();
            assertThat(resultadoAtestacao.deviceRecognitionVerdict()).contains("APPLE_APP_ATTEST_OK");
            assertThat(persistido.get()).isNotNull();
            assertThat(persistido.get().getChaveId()).isEqualTo(CHAVE_ID);
            assertThat(persistido.get().getObjetoAtestacaoBase64()).isEqualTo(OBJETO_ATESTACAO_BASE64);
        }

        long contadorAtestado = persistido.get().getContadorAssinatura();

        ValidacaoOficialAtestacaoAppResultado resultadoAssercao = validador.validar(
                criarComprovante(TipoComprovanteAtestacaoApp.OBJETO_ASSERCAO, OBJETO_ASSERCAO_BASE64),
                validacaoLocal
        );

        assertThat(resultadoAssercao.executada()).isTrue();
        assertThat(resultadoAssercao.deviceRecognitionVerdict()).contains("APPLE_APP_ASSERTION_OK");
        assertThat(persistido.get().getContadorAssinatura()).isGreaterThan(contadorAtestado);
    }

    private static AtestacaoAppProperties criarProperties() {
        AppleAppAttestProperties apple = new AppleAppAttestProperties();
        apple.setHabilitado(true);
        apple.setTeamIdentifier(TEAM_IDENTIFIER);
        apple.setBundleIdentifier(BUNDLE_IDENTIFIER);

        AtestacaoAppProperties properties = new AtestacaoAppProperties();
        properties.setApple(apple);
        return properties;
    }

    private static ComprovanteAtestacaoAppEntrada criarComprovante(final TipoComprovanteAtestacaoApp tipo,
                                                                   final String conteudoComprovante) {
        return new ComprovanteAtestacaoAppEntrada(
                PlataformaAtestacaoApp.IOS,
                ProvedorAtestacaoApp.APPLE_APP_ATTEST,
                tipo,
                "desafio-123",
                DESAFIO_BASE64,
                conteudoComprovante,
                GERADO_EM,
                CHAVE_ID
        );
    }
}
