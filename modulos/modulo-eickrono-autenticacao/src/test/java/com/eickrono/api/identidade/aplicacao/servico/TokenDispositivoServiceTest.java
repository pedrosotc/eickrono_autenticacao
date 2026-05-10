package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenDispositivoServiceTest {

    private static final Clock CLOCK_FIXO = Clock.fixed(Instant.parse("2024-05-10T10:15:30Z"), ZoneOffset.UTC);

    private TokenDispositivoService tokenDispositivoService;
    private DispositivoProperties propriedades;
    private RegistroDispositivo registroDispositivo;
    private DispositivoIdentidade dispositivoIdentidade;
    private List<TokenDispositivo> tokensPersistidos;
    private TokenDispositivoRepositorio tokenRepositorio;

    private TokenDispositivoRepositorio criarRepositorioFake() {
        return (TokenDispositivoRepositorio) Proxy.newProxyInstance(
                TokenDispositivoRepositorio.class.getClassLoader(),
                new Class<?>[]{TokenDispositivoRepositorio.class},
                (proxy, method, args) -> {
                    String nome = method.getName();
                    if ("save".equals(nome)) {
                        TokenDispositivo token = Objects.requireNonNull((TokenDispositivo) args[0]);
                        tokensPersistidos.removeIf(atual -> atual.getId().equals(token.getId()));
                        tokensPersistidos.add(token);
                        return token;
                    }
                    if ("findByUsuarioSubAndStatus".equals(nome)) {
                        String usuarioSub = Objects.requireNonNull((String) args[0]);
                        StatusTokenDispositivo status = Objects.requireNonNull((StatusTokenDispositivo) args[1]);
                        return tokensPersistidos.stream()
                                .filter(token -> usuarioSub.equals(token.getUsuarioSub()) && token.getStatus() == status)
                                .toList();
                    }
                    if ("findByUsuarioSubAndTokenHashAndStatus".equals(nome)) {
                        String usuarioSub = Objects.requireNonNull((String) args[0]);
                        String tokenHash = Objects.requireNonNull((String) args[1]);
                        StatusTokenDispositivo status = Objects.requireNonNull((StatusTokenDispositivo) args[2]);
                        return tokensPersistidos.stream()
                                .filter(token -> usuarioSub.equals(token.getUsuarioSub())
                                        && tokenHash.equals(token.getTokenHash())
                                        && token.getStatus() == status)
                                .findFirst();
                    }
                    if ("findByUsuarioSubAndTokenHash".equals(nome)) {
                        String usuarioSub = Objects.requireNonNull((String) args[0]);
                        String tokenHash = Objects.requireNonNull((String) args[1]);
                        return tokensPersistidos.stream()
                                .filter(token -> usuarioSub.equals(token.getUsuarioSub())
                                        && tokenHash.equals(token.getTokenHash()))
                                .findFirst();
                    }
                    if ("findByTokenHash".equals(nome)) {
                        String tokenHash = Objects.requireNonNull((String) args[0]);
                        return tokensPersistidos.stream()
                                .filter(token -> tokenHash.equals(token.getTokenHash()))
                                .findFirst();
                    }
                    if ("toString".equals(nome)) {
                        return "TokenDispositivoRepositorioFake";
                    }
                    throw new UnsupportedOperationException("Metodo nao suportado no fake: " + nome);
                });
    }

    /**
     * Configura o serviço de tokens com propriedades controladas e um registro de dispositivo base.
     * O Clock fixo garante previsibilidade para validar tempos de emissão e expiração.
     */
    private void inicializarServico() {
        tokensPersistidos = new ArrayList<>();
        tokenRepositorio = criarRepositorioFake();
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
        Pessoa pessoa = new Pessoa(
                "usuario-123",
                "usuario@test.com",
                "Usuario Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.now(CLOCK_FIXO));
        dispositivoIdentidade = new DispositivoIdentidade(
                pessoa,
                registroDispositivo.getFingerprint(),
                registroDispositivo.getPlataforma(),
                registroDispositivo.getVersaoAplicativo().orElse(null),
                null,
                StatusDispositivoIdentidade.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO),
                OffsetDateTime.now(CLOCK_FIXO));
    }

    /**
     * Cenário: emissão de novo token quando já existe um ativo.
     * Verificamos que o token anterior é revogado e que o novo é persistido com os carimbos esperados.
     */
    @Test
    void deveEmitirTokenRevogandoAnteriores() {
        inicializarServico();
        TokenDispositivo tokenAnterior = new TokenDispositivo(
                UUID.randomUUID(),
                registroDispositivo,
                dispositivoIdentidade,
                "usuario-123",
                "android|pixel",
                "Android",
                "0.9.0",
                "hash-antigo",
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO).minusHours(2),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(12)
        );

        tokensPersistidos.add(tokenAnterior);

        TokenDispositivoService.TokenEmitido tokenEmitido =
                tokenDispositivoService.emitirToken(registroDispositivo, dispositivoIdentidade, "usuario-123");

        assertThat(tokenEmitido.tokenClaro()).isNotBlank();
        assertThat(tokenEmitido.entidade().getStatus()).isEqualTo(StatusTokenDispositivo.ATIVO);
        assertThat(tokenEmitido.entidade().getEmitidoEm()).isEqualTo(OffsetDateTime.now(CLOCK_FIXO));
        assertThat(tokenAnterior.getStatus()).isEqualTo(StatusTokenDispositivo.REVOGADO);
        assertThat(tokenAnterior.getMotivoRevogacao()).contains(MotivoRevogacaoToken.NOVO_DISPOSITIVO_CONFIRMANDO);

        assertThat(tokensPersistidos)
                .anyMatch(token -> token.getId().equals(tokenEmitido.entidade().getId()));
        assertThat(tokensPersistidos.stream()
                .filter(token -> token.getStatus() == StatusTokenDispositivo.ATIVO)
                .count()).isEqualTo(1L);
    }

    /**
     * Cenário: validação de um token recém emitido.
     * Após salvar a entidade, simulamos a busca por hash e garantimos que o serviço o reconhece como ativo.
     */
    @Test
    void deveValidarTokenAtivo() {
        inicializarServico();
        TokenDispositivoService.TokenEmitido tokenEmitido =
                tokenDispositivoService.emitirToken(registroDispositivo, dispositivoIdentidade, "usuario-123");

        Optional<TokenDispositivo> resultado = tokenDispositivoService.validarTokenAtivo("usuario-123", tokenEmitido.tokenClaro());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().estaAtivo(OffsetDateTime.now(CLOCK_FIXO))).isTrue();
    }

    @Test
    void deveClassificarTokenRevogado() {
        inicializarServico();
        TokenDispositivoService.TokenEmitido tokenEmitido =
                tokenDispositivoService.emitirToken(registroDispositivo, dispositivoIdentidade, "usuario-123");
        tokenEmitido.entidade().revogar(MotivoRevogacaoToken.SOLICITACAO_CLIENTE, OffsetDateTime.now(CLOCK_FIXO));

        ResultadoValidacaoTokenDispositivo resultado = tokenDispositivoService.validarToken("usuario-123", tokenEmitido.tokenClaro());

        assertThat(resultado.status()).isEqualTo(StatusValidacaoTokenDispositivo.REVOGADO);
    }

    @Test
    void deveClassificarTokenExpirado() {
        inicializarServico();
        TokenDispositivoService.TokenEmitido tokenEmitido =
                tokenDispositivoService.emitirToken(registroDispositivo, dispositivoIdentidade, "usuario-123");
        TokenDispositivo tokenExpirado = new TokenDispositivo(
                tokenEmitido.entidade().getId(),
                registroDispositivo,
                dispositivoIdentidade,
                "usuario-123",
                tokenEmitido.entidade().getFingerprint(),
                tokenEmitido.entidade().getPlataforma(),
                tokenEmitido.entidade().getVersaoAplicativo().orElse(null),
                tokenEmitido.entidade().getTokenHash(),
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO).minusHours(48),
                OffsetDateTime.now(CLOCK_FIXO).minusMinutes(1)
        );
        tokensPersistidos.removeIf(token -> token.getId().equals(tokenEmitido.entidade().getId()));
        tokensPersistidos.add(tokenExpirado);

        ResultadoValidacaoTokenDispositivo resultado = tokenDispositivoService.validarToken("usuario-123", tokenEmitido.tokenClaro());

        assertThat(resultado.status()).isEqualTo(StatusValidacaoTokenDispositivo.EXPIRADO);
    }

    /**
     * Cenário: revogação manual solicitada pelo cliente.
     * Esperamos que o token ativo seja marcado como revogado e leve o motivo correto.
     */
    @Test
    void deveRevogarTokensPorSolicitacaoCliente() {
        inicializarServico();
        TokenDispositivo tokenAtivo = new TokenDispositivo(
                UUID.randomUUID(),
                registroDispositivo,
                dispositivoIdentidade,
                "usuario-123",
                "fingerprint",
                "Android",
                "1.0.0",
                "hash-ativo",
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(24)
        );

        tokensPersistidos.add(tokenAtivo);

        tokenDispositivoService.revogarTokensAtivos("usuario-123", MotivoRevogacaoToken.SOLICITACAO_CLIENTE);

        assertThat(tokenAtivo.getStatus()).isEqualTo(StatusTokenDispositivo.REVOGADO);
        assertThat(tokenAtivo.getMotivoRevogacao()).contains(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
    }

    /**
     * Teste auxiliar que valida se a rotina explícita de inicialização prepara as propriedades padrão.
     */
    @Test
    void devePrepararServicoComPropriedadesPadrao() {
        inicializarServico();
        assertThat(tokenDispositivoService).as("Serviço deve ser instanciado no setUp").isNotNull();
        assertThat(propriedades.getToken().getTamanhoBytes()).isEqualTo(16);
        assertThat(propriedades.getToken().getValidadeHoras()).isEqualTo(48);
    }
}
