package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.LoginSocialProjetoResolvido;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class LocalizadorLoginSocialProjetoJdbcTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final LocalizadorLoginSocialProjetoJdbc localizador = new LocalizadorLoginSocialProjetoJdbc(jdbcTemplate);

    @Test
    void naoConsultaBancoQuandoEntradaEstaIncompleta() {
        assertThat(localizador.localizar(null, "GOOGLE", "google-sub")).isEmpty();
        assertThat(localizador.localizar(1L, "", "google-sub")).isEmpty();
        assertThat(localizador.localizar(1L, "GOOGLE", " ")).isEmpty();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void localizaVinculoSocialDefinitivoDoProjeto() throws Exception {
        UUID usuarioId = UUID.fromString("81818181-8181-8181-8181-818181818181");
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("usuario_id", UUID.class)).thenReturn(usuarioId);
        when(resultSet.getString("sub_remoto")).thenReturn("usuario-social");
        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<LoginSocialProjetoResolvido>>any()))
                .thenAnswer(invocation -> {
                    RowMapper<LoginSocialProjetoResolvido> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        Optional<LoginSocialProjetoResolvido> resultado = localizador.localizar(77L, "google", "google-sub-123");

        assertThat(resultado).contains(new LoginSocialProjetoResolvido(usuarioId, "usuario-social"));
    }
}
