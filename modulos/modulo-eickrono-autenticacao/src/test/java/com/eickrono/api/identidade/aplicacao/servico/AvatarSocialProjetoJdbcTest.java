package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @DisplayName("sincronizacao social deve criar vinculo multiapp com identificador publico preenchido")
    void sincronizacaoSocialDeveCriarVinculoMultiappComIdentificadorPublicoClientePreenchido() {
        OffsetDateTime agora = OffsetDateTime.parse("2026-06-02T12:00:00Z");
        AvatarSocialProjetoJdbc jdbc = new AvatarSocialProjetoJdbc(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));

        jdbc.sincronizar(
                "sub-social-cenario",
                "social@example.com",
                77L,
                agora,
                agora,
                " social-user ",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-cenario",
                        "social@example.com",
                        "Usuario Social",
                        "https://cdn.example.com/avatar.png")));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sqlVinculo = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("INSERT INTO autenticacao.usuarios_clientes_ecossistema"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Insert do vinculo multiapp nao foi chamado."));
        assertThat(sqlVinculo)
                .doesNotContainPattern(
                        "(?s)identificador_publico_cliente.*VALUES \\(.*:statusVinculo,\\s*NULL,");
        MapSqlParameterSource paramsVinculo = localizarParams(
                sqlCaptor,
                paramsCaptor,
                "INSERT INTO autenticacao.usuarios_clientes_ecossistema");
        assertThat(paramsVinculo.getValue("identificadorPublicoCliente")).isEqualTo("social-user");
    }

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

    private static MapSqlParameterSource localizarParams(final ArgumentCaptor<String> sqlCaptor,
                                                         final ArgumentCaptor<MapSqlParameterSource> paramsCaptor,
                                                         final String sqlEsperado) {
        List<String> sqls = sqlCaptor.getAllValues();
        for (int i = 0; i < sqls.size(); i++) {
            if (sqls.get(i).contains(sqlEsperado)) {
                return paramsCaptor.getAllValues().get(i);
            }
        }
        throw new AssertionError("SQL não encontrado: " + sqlEsperado);
    }
}
