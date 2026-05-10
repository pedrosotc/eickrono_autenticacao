package com.eickrono.api.identidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class AplicacaoApiIdentidadeTest {

    private static final String EXPECTED_AUDIENCE = "eickrono-autenticacao";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void deveCarregarContextoEAplicarMigracoes() {
        assertThat(applicationContext).isNotNull();
        assertThat(flyway.info().current())
                .as("Flyway deve aplicar ao menos uma migração antes dos testes")
                .isNotNull();
    }

    @Test
    void deveValidarJwtEmitidoPeloOidcSimulado() throws Exception {
        RSAKey chave = InfraestruturaTesteIdentidade.obterRsaKey();
        String token = emitirJwtAssinado(chave, chave.getKeyID(), Duration.ofMinutes(15));

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("usuario-de-teste");
        assertThat(jwt.getIssuer().toString()).isEqualTo(InfraestruturaTesteIdentidade.obterIssuer());
        assertThat(jwt.getAudience()).contains(EXPECTED_AUDIENCE);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void deveRecusarJwtComAssinaturaNaoReconhecida() throws Exception {
        RSAKey outraChave = gerarChaveJwt();
        String token = emitirJwtAssinado(outraChave, outraChave.getKeyID(), Duration.ofMinutes(5));

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("no matching key");
    }

    @Test
    void deveRecusarJwtExpirado() throws Exception {
        RSAKey chave = InfraestruturaTesteIdentidade.obterRsaKey();
        Instant emissao = Instant.now().minusSeconds(3600);
        Instant expiracao = Instant.now().minusSeconds(60);
        String token = emitirJwtAssinado(chave, chave.getKeyID(), expiracao, emissao);

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @AfterAll
    static void encerrarInfraestrutura() {
        InfraestruturaTesteIdentidade.encerrarInfraestrutura();
    }

    private static RSAKey gerarChaveJwt() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("chave-invalida-" + UUID.randomUUID())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao gerar chave RSA para os testes de JWT", e);
        }
    }

    private static String emitirJwtAssinado(RSAKey key, String kid, Duration validade) throws JOSEException {
        Instant agora = Instant.now();
        Instant expiracao = agora.plus(validade);
        return emitirJwtAssinado(key, kid, expiracao, agora);
    }

    private static String emitirJwtAssinado(RSAKey key, String kid, Instant expiracao, Instant agora)
            throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(InfraestruturaTesteIdentidade.obterIssuer())
                .subject("usuario-de-teste")
                .audience(EXPECTED_AUDIENCE)
                .issueTime(Date.from(agora))
                .notBeforeTime(Date.from(agora.minusSeconds(5)))
                .expirationTime(Date.from(expiracao))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
                claims);

        JWSSigner signer = new RSASSASigner(key);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
