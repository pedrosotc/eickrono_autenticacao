package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AvatarSocialProjetoJdbcTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void deveBuscarAvatarPreferidoNoModeloCanonicoComVersao() throws Exception {
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-05-20T12:00:00Z");
        AvatarSocialProjetoJdbc jdbc = new AvatarSocialProjetoJdbc(jdbcTemplate);
        when(jdbcTemplate.query(
                contains("identidade.avatar_usuario"),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto>>any()))
                .thenAnswer(invocation -> {
                    RowMapper<AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto> mapper = invocation.getArgument(2);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("avatar_preferido_origem")).thenReturn("SOCIAL");
                    when(resultSet.getString("avatar_preferido_url"))
                            .thenReturn("https://cdn.eickrono.test/google.png");
                    when(resultSet.getString("provedor_social")).thenReturn("GOOGLE");
                    when(resultSet.getString("avatar_preferido_versao")).thenReturn("avatar-v-google");
                    when(resultSet.getObject("avatar_preferido_atualizado_em", OffsetDateTime.class))
                            .thenReturn(atualizadoEm);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto preferencia =
                jdbc.buscarPreferencia("sub-pedro", 77L);

        assertThat(preferencia.origem()).isEqualTo("SOCIAL");
        assertThat(preferencia.url()).isEqualTo("https://cdn.eickrono.test/google.png");
        assertThat(preferencia.provedorSocial()).isEqualTo("GOOGLE");
        assertThat(preferencia.versao()).isEqualTo("avatar-v-google");
        assertThat(preferencia.atualizadoEm()).isEqualTo(atualizadoEm);
    }

    @Test
    void deveUsarPreferenciaLegadaQuandoTabelaCanonicaAindaNaoExiste() throws Exception {
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-05-20T12:30:00Z");
        AvatarSocialProjetoJdbc jdbc = new AvatarSocialProjetoJdbc(jdbcTemplate);
        when(jdbcTemplate.query(
                contains("identidade.avatar_usuario"),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto>>any()))
                .thenThrow(new BadSqlGrammarException(
                        "buscarAvatar",
                        "SELECT * FROM identidade.avatar_usuario",
                        new SQLException("relation \"identidade.avatar_usuario\" does not exist")));
        when(jdbcTemplate.query(
                contains("uce.avatar_preferido_origem"),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto>>any()))
                .thenAnswer(invocation -> {
                    RowMapper<AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto> mapper = invocation.getArgument(2);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("avatar_preferido_origem")).thenReturn("SOCIAL");
                    when(resultSet.getString("avatar_preferido_url"))
                            .thenReturn("https://cdn.eickrono.test/legado.png");
                    when(resultSet.getString("provedor_social")).thenReturn("GOOGLE");
                    when(resultSet.getObject("avatar_preferido_atualizado_em", OffsetDateTime.class))
                            .thenReturn(atualizadoEm);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto preferencia =
                jdbc.buscarPreferencia("sub-pedro", 77L);

        assertThat(preferencia.origem()).isEqualTo("SOCIAL");
        assertThat(preferencia.url()).isEqualTo("https://cdn.eickrono.test/legado.png");
        assertThat(preferencia.provedorSocial()).isEqualTo("GOOGLE");
        assertThat(preferencia.versao()).isNull();
        assertThat(preferencia.atualizadoEm()).isEqualTo(atualizadoEm);
    }
}
