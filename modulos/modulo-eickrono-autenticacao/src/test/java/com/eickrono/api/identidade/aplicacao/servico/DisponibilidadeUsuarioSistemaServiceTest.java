package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeUsuarioSistemaServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private DisponibilidadeUsuarioSistemaService service;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadeUsuarioSistemaService(jdbcTemplate);
    }

    @Test
    @DisplayName("deve bloquear o mesmo usuario no mesmo sistema")
    void deveBloquearMesmoUsuarioNoMesmoSistema() {
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return sql.contains("FROM autenticacao.cadastros_conta");
                });

        boolean disponivel = service.usuarioDisponivel(" Ana.Souza ", "eickrono-thimisu-app");

        assertThat(disponivel).isFalse();
    }

    @Test
    @DisplayName("deve permitir o mesmo usuario em outro sistema")
    void devePermitirMesmoUsuarioEmOutroSistema() {
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenReturn(false);

        boolean disponivel = service.usuarioDisponivel(" Ana.Souza ", "outro-sistema");

        assertThat(disponivel).isTrue();
    }

    @Test
    @DisplayName("deve bloquear o usuario do alias legado usando o cliente atual")
    void deveBloquearUsuarioDoAliasLegadoUsandoOClienteAtual() {
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    MapSqlParameterSource params = invocation.getArgument(1, MapSqlParameterSource.class);
                    if (sql.contains("FROM autenticacao.usuarios_clientes_ecossistema")) {
                        return false;
                    }
                    return "eickrono-thimisu-app".equals(params.getValue("codigoCliente"));
                });

        boolean disponivel = service.usuarioDisponivel(" Ana.Souza ", "app-flutter-publico");

        assertThat(disponivel).isFalse();
    }

    @Test
    @DisplayName("deve bloquear o usuario quando o vinculo multiapp ja existir")
    void deveBloquearUsuarioQuandoVinculoMultiappJaExistir() {
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return sql.contains("FROM autenticacao.usuarios_clientes_ecossistema");
                });

        boolean disponivel = service.usuarioDisponivel(" Ana.Souza ", "eickrono-thimisu-app");

        assertThat(disponivel).isFalse();
    }
}
