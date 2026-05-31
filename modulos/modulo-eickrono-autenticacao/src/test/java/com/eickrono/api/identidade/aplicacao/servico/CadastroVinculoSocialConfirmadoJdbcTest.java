package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.VinculoSocialConfirmadoCadastro;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CadastroVinculoSocialConfirmadoJdbcTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void deveRegistrarVinculosConfirmadosNormalizadosPorCadastro() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC);
        CadastroVinculoSocialConfirmadoJdbc repositorio =
                new CadastroVinculoSocialConfirmadoJdbc(jdbcTemplate, clock);
        UUID cadastroId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        repositorio.registrar(
                cadastroId,
                List.of(new VinculoSocialConfirmadoCadastro(
                        " Google ",
                        " google-123 ",
                        " ana.google ",
                        " ANA@SOCIAL.TEST ",
                        " Ana Social ",
                        " https://cdn.test/avatar.png ",
                        true
                ))
        );

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(anyString(), paramsCaptor.capture());
        MapSqlParameterSource insertParams = paramsCaptor.getAllValues().get(1);
        assertThat(insertParams.getValue("cadastroId")).isEqualTo(cadastroId);
        assertThat(insertParams.getValue("provedor")).isEqualTo("Google");
        assertThat(insertParams.getValue("identificadorExterno")).isEqualTo("google-123");
        assertThat(insertParams.getValue("nomeUsuarioExterno")).isEqualTo("ana.google");
        assertThat(insertParams.getValue("emailSocial")).isEqualTo("ana@social.test");
        assertThat(insertParams.getValue("nomeExibicaoExterno")).isEqualTo("Ana Social");
        assertThat(insertParams.getValue("urlAvatarExterno")).isEqualTo("https://cdn.test/avatar.png");
        assertThat(insertParams.getValue("avatarPreferido")).isEqualTo(true);
        assertThat(insertParams.getValue("criadoEm"))
                .isEqualTo(OffsetDateTime.parse("2026-05-20T12:00:00Z"));
    }

    @Test
    void deveConsumirApenasVinculosAtivosDoCadastro() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC);
        CadastroVinculoSocialConfirmadoJdbc repositorio =
                new CadastroVinculoSocialConfirmadoJdbc(jdbcTemplate, clock);
        UUID cadastroId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        repositorio.consumir(cadastroId);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("cadastroId")).isEqualTo(cadastroId);
        assertThat(paramsCaptor.getValue().getValue("consumidoEm"))
                .isEqualTo(OffsetDateTime.parse("2026-05-20T12:00:00Z"));
    }
}
