package com.eickrono.api.identidade.aplicacao.servico;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;

class CanalEnvioCodigoSmsTest {

    @Test
    @DisplayName("deve delegar o envio SMS para o fornecedor configurado")
    void deveDelegarParaFornecedorConfigurado() {
        DispositivoProperties properties = new DispositivoProperties();
        properties.getOnboarding().setSmsFornecedor("fornecedor-b");

        FornecedorSmsFake fornecedorA = new FornecedorSmsFake("fornecedor-a");
        FornecedorSmsFake fornecedorB = new FornecedorSmsFake("fornecedor-b");
        CanalEnvioCodigoSms canal = new CanalEnvioCodigoSms(properties, List.of(fornecedorA, fornecedorB));

        canal.enviar(registro(), "+5511999990000", "123456");

        assertThat(fornecedorA.destinos).isEmpty();
        assertThat(fornecedorB.destinos).containsExactly("+5511999990000");
        assertThat(fornecedorB.codigos).containsExactly("123456");
    }

    @Test
    @DisplayName("deve falhar quando o fornecedor de SMS configurado não existir")
    void deveFalharQuandoFornecedorNaoExistir() {
        DispositivoProperties properties = new DispositivoProperties();
        properties.getOnboarding().setSmsFornecedor("inexistente");
        CanalEnvioCodigoSms canal = new CanalEnvioCodigoSms(properties, List.of(new FornecedorSmsFake("log")));

        assertThatThrownBy(() -> canal.enviar(registro(), "+5511999990000", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private RegistroDispositivo registro() {
        return new RegistroDispositivo(
                UUID.randomUUID(),
                "sub-123",
                "teste@eickrono.com",
                "+5511999990000",
                "fingerprint",
                "ANDROID",
                "1.0.0",
                null,
                StatusRegistroDispositivo.PENDENTE,
                OffsetDateTime.parse("2026-03-11T12:00:00Z"),
                OffsetDateTime.parse("2026-03-12T12:00:00Z"));
    }

    private static final class FornecedorSmsFake implements FornecedorEnvioSms {
        private final String identificador;
        private final java.util.List<String> destinos = new java.util.ArrayList<>();
        private final java.util.List<String> codigos = new java.util.ArrayList<>();

        private FornecedorSmsFake(String identificador) {
            this.identificador = identificador;
        }

        @Override
        public String identificador() {
            return identificador;
        }

        @Override
        public void enviarSms(RegistroDispositivo registro, String destino, String codigo) {
            destinos.add(destino);
            codigos.add(codigo);
        }
    }
}
