package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.AvatarCadastroConfirmado;
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
class CadastroAvatarConfirmadoJdbcTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private UploadAvatarCadastroServico uploadAvatarCadastroServico;

    @Test
    void deveRegistrarAvatarConfirmadoNormalizadoPorCadastro() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC);
        CadastroAvatarConfirmadoJdbc repositorio = new CadastroAvatarConfirmadoJdbc(jdbcTemplate, clock);
        UUID cadastroId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        repositorio.registrar(
                cadastroId,
                List.of(new AvatarCadastroConfirmado(
                        " thimisu ",
                        " https://cdn.eickrono.test/avatar.png ",
                        " usuarios/sub/avatar.png ",
                        null,
                        " image/png ",
                        9876L,
                        " hash ",
                        " versao ",
                        null,
                        true
                ))
        );

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(anyString(), paramsCaptor.capture());
        MapSqlParameterSource insertParams = paramsCaptor.getAllValues().get(1);
        assertThat(insertParams.getValue("cadastroId")).isEqualTo(cadastroId);
        assertThat(insertParams.getValue("origem")).isEqualTo("THIMISU");
        assertThat(insertParams.getValue("urlAvatar")).isEqualTo("https://cdn.eickrono.test/avatar.png");
        assertThat(insertParams.getValue("storageKey")).isEqualTo("usuarios/sub/avatar.png");
        assertThat(insertParams.getValue("contentType")).isEqualTo("image/png");
        assertThat(insertParams.getValue("tamanhoBytes")).isEqualTo(9876L);
        assertThat(insertParams.getValue("hashConteudo")).isEqualTo("hash");
        assertThat(insertParams.getValue("versao")).isEqualTo("versao");
        assertThat(insertParams.getValue("preferido")).isEqualTo(true);
        assertThat(insertParams.getValue("criadoEm"))
                .isEqualTo(OffsetDateTime.parse("2026-05-20T12:00:00Z"));
    }

    @Test
    void deveMaterializarAvatarComConteudoAntesDeRegistrar() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC);
        CadastroAvatarConfirmadoJdbc repositorio = new CadastroAvatarConfirmadoJdbc(
                jdbcTemplate,
                clock,
                uploadAvatarCadastroServico
        );
        UUID cadastroId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        AvatarCadastroConfirmado avatarPendente = new AvatarCadastroConfirmado(
                "THIMISU",
                null,
                null,
                "avatar.png",
                "image/png",
                3L,
                null,
                null,
                "AQID",
                false
        );
        when(uploadAvatarCadastroServico.materializar(avatarPendente)).thenReturn(new AvatarCadastroConfirmado(
                "THIMISU",
                "https://cdn.eickrono.test/avatar.png",
                "usuarios/sub/avatar.png",
                "avatar.png",
                "image/png",
                3L,
                "hash-upload",
                "versao-upload",
                null,
                false
        ));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        repositorio.registrar(cadastroId, List.of(avatarPendente));

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(uploadAvatarCadastroServico).materializar(avatarPendente);
        verify(jdbcTemplate, times(2)).update(anyString(), paramsCaptor.capture());
        MapSqlParameterSource insertParams = paramsCaptor.getAllValues().get(1);
        assertThat(insertParams.getValue("urlAvatar")).isEqualTo("https://cdn.eickrono.test/avatar.png");
        assertThat(insertParams.getValue("storageKey")).isEqualTo("usuarios/sub/avatar.png");
        assertThat(insertParams.getValue("hashConteudo")).isEqualTo("hash-upload");
        assertThat(insertParams.getValue("versao")).isEqualTo("versao-upload");
        assertThat(insertParams.getValue("preferido")).isEqualTo(false);
    }

    @Test
    void deveConsumirAvatarConfirmadoParaUsuarioCliente() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC);
        CadastroAvatarConfirmadoJdbc repositorio = new CadastroAvatarConfirmadoJdbc(jdbcTemplate, clock);
        UUID cadastroId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        repositorio.consumirParaUsuario(
                cadastroId,
                "sub-ana",
                99L,
                OffsetDateTime.parse("2026-05-20T13:00:00Z")
        );

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(anyString(), paramsCaptor.capture());
        MapSqlParameterSource params = paramsCaptor.getAllValues().get(1);
        assertThat(params.getValue("cadastroId")).isEqualTo(cadastroId);
        assertThat(params.getValue("subjectRemoto")).isEqualTo("sub-ana");
        assertThat(params.getValue("clienteEcossistemaId")).isEqualTo(99L);
        assertThat(params.getValue("atualizadoEm"))
                .isEqualTo(OffsetDateTime.parse("2026-05-20T13:00:00Z"));
    }
}
