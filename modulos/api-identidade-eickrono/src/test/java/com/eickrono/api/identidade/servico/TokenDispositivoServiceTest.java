package com.eickrono.api.identidade.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenDispositivoServiceTest {

    private static final Clock CLOCK_FIXO = Clock.fixed(Instant.parse("2024-05-10T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private TokenDispositivoRepositorio tokenRepositorio;

    private TokenDispositivoService tokenDispositivoService;
    private DispositivoProperties propriedades;
    private RegistroDispositivo registroDispositivo;

    /**
     * Configura o serviço de tokens com propriedades controladas e um registro de dispositivo base.
     * O Clock fixo garante previsibilidade para validar tempos de emissão e expiração.
     */
    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        propriedades = new DispositivoProperties();
        propriedades.getToken().setSegredoHmac("segredo-test-token");
        propriedades.getToken().setTamanhoBytes(16);
        propriedades.getToken().setValidadeHoras(48);

        tokenDispositivoService = new TokenDispositivoService(tokenRepositorio, propriedades, CLOCK_FIXO);

        registroDispositivo = new RegistroDispositivo(
                UUID.randomUUID(),
                "usuario-123",
                "usuario@test.com",
                "+5511999990000",
                "ios|iphone14,3|abcd",
                "iOS",
                "1.0.0",
                null,
                StatusRegistroDispositivo.PENDENTE,
                OffsetDateTime.now(CLOCK_FIXO),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(9)
        );
    }

    /**
     * Cenário: emissão de novo token quando já existe um ativo.
     * Verificamos que o token anterior é revogado e que o novo é persistido com os carimbos esperados.
     */
    @Test
    void deveEmitirTokenRevogandoAnteriores() {
        TokenDispositivo tokenAnterior = new TokenDispositivo(
                UUID.randomUUID(),
                registroDispositivo,
                "usuario-123",
                "android|pixel",
                "Android",
                "0.9.0",
                "hash-antigo",
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO).minusHours(2),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(12)
        );

        when(tokenRepositorio.findByUsuarioSubAndStatus("usuario-123", StatusTokenDispositivo.ATIVO))
                .thenReturn(List.of(tokenAnterior));
        when(tokenRepositorio.save(any(TokenDispositivo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        TokenDispositivoService.TokenEmitido tokenEmitido = tokenDispositivoService.emitirToken(registroDispositivo, "usuario-123");

        assertThat(tokenEmitido.tokenClaro()).isNotBlank();
        assertThat(tokenEmitido.entidade().getStatus()).isEqualTo(StatusTokenDispositivo.ATIVO);
        assertThat(tokenEmitido.entidade().getEmitidoEm()).isEqualTo(OffsetDateTime.now(CLOCK_FIXO));
        assertThat(tokenAnterior.getStatus()).isEqualTo(StatusTokenDispositivo.REVOGADO);

        verify(tokenRepositorio).save(any(TokenDispositivo.class));
    }

    /**
     * Cenário: validação de um token recém emitido.
     * Após salvar a entidade, simulamos a busca por hash e garantimos que o serviço o reconhece como ativo.
     */
    @Test
    void deveValidarTokenAtivo() {
        when(tokenRepositorio.findByUsuarioSubAndStatus("usuario-123", StatusTokenDispositivo.ATIVO))
                .thenReturn(List.of());
        when(tokenRepositorio.save(any(TokenDispositivo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        TokenDispositivoService.TokenEmitido tokenEmitido = tokenDispositivoService.emitirToken(registroDispositivo, "usuario-123");

        when(tokenRepositorio.findByUsuarioSubAndTokenHashAndStatus(
                "usuario-123",
                tokenEmitido.entidade().getTokenHash(),
                StatusTokenDispositivo.ATIVO))
                .thenReturn(Optional.of(tokenEmitido.entidade()));

        Optional<TokenDispositivo> resultado = tokenDispositivoService.validarTokenAtivo("usuario-123", tokenEmitido.tokenClaro());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().estaAtivo(OffsetDateTime.now(CLOCK_FIXO))).isTrue();
    }

    /**
     * Cenário: revogação manual solicitada pelo cliente.
     * Esperamos que o token ativo seja marcado como revogado e leve o motivo correto.
     */
    @Test
    void deveRevogarTokensPorSolicitacaoCliente() {
        TokenDispositivo tokenAtivo = new TokenDispositivo(
                UUID.randomUUID(),
                registroDispositivo,
                "usuario-123",
                "fingerprint",
                "Android",
                "1.0.0",
                "hash-ativo",
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(24)
        );

        when(tokenRepositorio.findByUsuarioSubAndStatus("usuario-123", StatusTokenDispositivo.ATIVO))
                .thenReturn(List.of(tokenAtivo));

        tokenDispositivoService.revogarTokensAtivos("usuario-123", MotivoRevogacaoToken.SOLICITACAO_CLIENTE);

        assertThat(tokenAtivo.getStatus()).isEqualTo(StatusTokenDispositivo.REVOGADO);
        assertThat(tokenAtivo.getMotivoRevogacao()).contains(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
    }

    /**
     * Teste auxiliar que valida se o @BeforeEach configurou corretamente as propriedades padrão.
     * Como o método é executado automaticamente antes de cada caso, aqui apenas verificamos seus efeitos.
     */
    @Test
    void setUpDevePrepararServicoComPropriedadesPadrao() {
        assertThat(tokenDispositivoService).as("Serviço deve ser instanciado no setUp").isNotNull();
        assertThat(propriedades.getToken().getTamanhoBytes()).isEqualTo(16);
        assertThat(propriedades.getToken().getValidadeHoras()).isEqualTo(48);
    }
}
