package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SincronizacaoModeloMultiappServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

    private SincronizacaoModeloMultiappService service;

    @BeforeEach
    void setUp() {
        service = new SincronizacaoModeloMultiappService(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Optional<Long>>>any()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("FROM catalogo.clientes_ecossistema")) {
                        return Optional.of(11L);
                    }
                    if (sql.contains("FROM catalogo.sistemas_origem")) {
                        return Optional.of(22L);
                    }
                    return Optional.empty();
                });
    }

    @Test
    @DisplayName("deve persistir o identificador publico do sistema no vinculo multiapp do cadastro confirmado")
    void devePersistirIdentificadorPublicoDoSistemaNoVinculoMultiappDoCadastroConfirmado() {
        OffsetDateTime agora = OffsetDateTime.parse("2026-05-02T12:00:00Z");
        CadastroConta cadastroConta = new CadastroConta(
                java.util.UUID.randomUUID(),
                "sub-ana",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                " Ana.Souza ",
                null,
                "BR",
                LocalDate.parse("1990-01-10"),
                "ana@eickrono.com",
                "+5511999999999",
                null,
                "hash-codigo",
                agora,
                agora.plusHours(9),
                "app-flutter-publico",
                "127.0.0.1",
                "JUnit",
                agora,
                agora
        );
        cadastroConta.marcarEmailConfirmado(agora.plusMinutes(1));

        service.sincronizarCadastro(cadastroConta);

        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), paramsCaptor.capture());

        MapSqlParameterSource paramsCliente = localizarParams("INSERT INTO catalogo.clientes_ecossistema");
        assertThat(paramsCliente.getValue("codigo")).isEqualTo("eickrono-thimisu-app");

        MapSqlParameterSource paramsVinculo = localizarParams("INSERT INTO autenticacao.usuarios_clientes_ecossistema");
        assertThat(paramsVinculo.getValue("clienteEcossistemaId")).isEqualTo(11L);
        assertThat(paramsVinculo.getValue("identificadorPublicoCliente")).isEqualTo("ana.souza");

        MapSqlParameterSource paramsCadastro = localizarParams("INSERT INTO autenticacao.cadastros_conta");
        assertThat(paramsCadastro.getValue("identificadorPublicoCliente")).isEqualTo("ana.souza");
    }

    private MapSqlParameterSource localizarParams(final String sqlEsperado) {
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            if (sqlCaptor.getAllValues().get(i).contains(sqlEsperado)) {
                return paramsCaptor.getAllValues().get(i);
            }
        }
        throw new AssertionError("SQL não encontrado: " + sqlEsperado);
    }
}
