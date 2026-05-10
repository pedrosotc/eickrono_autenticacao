package com.eickrono.api.identidade.aplicacao.servico;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoCodigoRecuperacaoSenhaRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.RecuperacaoSenhaIniciada;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import com.eickrono.api.identidade.dominio.repositorio.RecuperacaoSenhaRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private RecuperacaoSenhaRepositorio recuperacaoSenhaRepositorio;

    @Mock
    private ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;

    @Mock
    private CanalEnvioCodigoRecuperacaoSenhaEmail canalEnvioCodigoRecuperacaoSenhaEmail;

    @Captor
    private ArgumentCaptor<String> codigoCaptor;

    private RecuperacaoSenhaService servico;

    @BeforeEach
    void setUp() {
        DispositivoProperties dispositivoProperties = new DispositivoProperties();
        dispositivoProperties.getCodigo().setSegredoHmac("test-code-secret");
        dispositivoProperties.getCodigo().setTamanho(6);
        dispositivoProperties.getCodigo().setTentativasMaximas(5);
        dispositivoProperties.getCodigo().setReenviosMaximos(3);
        dispositivoProperties.getCodigo().setExpiracaoHoras(9);

        Clock clock = Clock.fixed(Instant.parse("2026-03-25T18:00:00Z"), ZoneOffset.UTC);
        servico = new RecuperacaoSenhaService(
                recuperacaoSenhaRepositorio,
                clienteAdministracaoCadastroKeycloak,
                canalEnvioCodigoRecuperacaoSenhaEmail,
                dispositivoProperties,
                clock
        );
    }

    @Test
    @DisplayName("deve iniciar, confirmar e redefinir a senha quando o e-mail existir")
    void deveExecutarFluxoCompletoDeRecuperacao() {
        AtomicReference<RecuperacaoSenha> salvo = new AtomicReference<>();
        when(clienteAdministracaoCadastroKeycloak.buscarUsuarioPorEmail("ana@eickrono.com"))
                .thenReturn(Optional.of(new UsuarioCadastroKeycloakExistente(
                        "sub-ana",
                        "ana@eickrono.com",
                        true,
                        true,
                        1L
                )));
        when(recuperacaoSenhaRepositorio.save(any(RecuperacaoSenha.class))).thenAnswer(invocation -> {
            RecuperacaoSenha recuperacao = invocation.getArgument(0);
            salvo.set(recuperacao);
            return recuperacao;
        });

        RecuperacaoSenhaIniciada iniciada = servico.iniciar("ana@eickrono.com");

        assertThat(iniciada.fluxoId()).isNotNull();
        verify(canalEnvioCodigoRecuperacaoSenhaEmail).enviar(any(RecuperacaoSenha.class), codigoCaptor.capture());
        assertThat(codigoCaptor.getValue()).matches("\\d{6}");
        when(recuperacaoSenhaRepositorio.findByFluxoId(iniciada.fluxoId())).thenReturn(Optional.of(salvo.get()));

        ConfirmacaoCodigoRecuperacaoSenhaRealizada confirmacao =
                servico.confirmarCodigo(iniciada.fluxoId(), codigoCaptor.getValue());

        assertThat(confirmacao.codigoConfirmado()).isTrue();
        assertThat(confirmacao.podeDefinirSenha()).isTrue();

        servico.redefinirSenha(iniciada.fluxoId(), "SenhaNova@123", "SenhaNova@123");

        verify(clienteAdministracaoCadastroKeycloak).redefinirSenha("sub-ana", "SenhaNova@123");
        assertThat(salvo.get().senhaJaRedefinida()).isTrue();
    }

    @Test
    @DisplayName("deve responder de forma neutra quando o e-mail não existir")
    void deveResponderDeFormaNeutraQuandoEmailNaoExistir() {
        AtomicReference<RecuperacaoSenha> salvo = new AtomicReference<>();
        when(clienteAdministracaoCadastroKeycloak.buscarUsuarioPorEmail("desconhecido@eickrono.com"))
                .thenReturn(Optional.empty());
        when(recuperacaoSenhaRepositorio.save(any(RecuperacaoSenha.class))).thenAnswer(invocation -> {
            RecuperacaoSenha recuperacao = invocation.getArgument(0);
            salvo.set(recuperacao);
            return recuperacao;
        });

        RecuperacaoSenhaIniciada iniciada = servico.iniciar("desconhecido@eickrono.com");

        assertThat(iniciada.fluxoId()).isNotNull();
        verify(canalEnvioCodigoRecuperacaoSenhaEmail, never()).enviar(any(), any());
        when(recuperacaoSenhaRepositorio.findByFluxoId(iniciada.fluxoId())).thenReturn(Optional.of(salvo.get()));

        assertThatThrownBy(() -> servico.confirmarCodigo(iniciada.fluxoId(), "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inválido");
    }
}
