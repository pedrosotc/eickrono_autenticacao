package com.eickrono.api.contas;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AplicacaoApiContasTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void deveCarregarContexto() {
        when(jwtDecoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "none").claim("sub", "test").build());
    }

    @SpringBootApplication
    static class App {}
}
