package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.AvaliacaoSegurancaAplicativoRealizada;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.SegurancaAplicativoApiRequest;
import com.eickrono.api.identidade.infraestrutura.configuracao.AtestacaoAppProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.SegurancaAplicativoProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvaliacaoSegurancaAplicativoServiceTest {

    @Mock
    private AuditoriaService auditoriaService;

    private SegurancaAplicativoProperties properties;
    private AtestacaoAppProperties atestacaoAppProperties;

    @BeforeEach
    void setUp() {
        properties = new SegurancaAplicativoProperties();
        properties.setAplicacoesPermitidas(List.of("eickrono-thimisu-app"));
        properties.setScoreMaximoPermitido(69);

        atestacaoAppProperties = new AtestacaoAppProperties();
        atestacaoAppProperties.getGoogle().setPackageName("com.eickrono.thimisu");
        atestacaoAppProperties.getApple().setBundleIdentifier("com.eickrono.thimisu");
        atestacaoAppProperties.getApple().setTeamIdentifier("TEAM12345");
    }

    @Test
    @DisplayName("deve apenas auditar em modo observacao quando sinais locais divergirem")
    void deveApenasAuditarEmModoObservacaoQuandoSinaisLocaisDivergirem() {
        properties.setModoObservacao(true);
        properties.setHabilitado(true);

        AvaliacaoSegurancaAplicativoService service = new AvaliacaoSegurancaAplicativoService(
                properties,
                atestacaoAppProperties,
                auditoriaService
        );

        SegurancaAplicativoApiRequest request = new SegurancaAplicativoApiRequest(
                "ANDROID",
                "GOOGLE_PLAY_INTEGRITY",
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                List.of("rootbeer_is_rooted"),
                50,
                "com.eickrono.thimisu",
                null,
                null,
                "abc"
        );

        AvaliacaoSegurancaAplicativoRealizada avaliacao = service.avaliar(
                "login",
                "eickrono-thimisu-app",
                "ANDROID",
                request,
                "usuario-123"
        );

        assertThat(avaliacao.bloqueada()).isFalse();
        assertThat(avaliacao.modoObservacao()).isTrue();
        assertThat(avaliacao.scoreRisco()).isGreaterThan(0);
        assertThat(avaliacao.sinaisCalculados()).contains("root_ou_jailbreak", "rootbeer_is_rooted");

        verify(auditoriaService).registrarEvento(
                eq("SEGURANCA_APP_LOGIN"),
                eq("usuario-123"),
                contains("aplicacaoId=eickrono-thimisu-app")
        );
    }

    @Test
    @DisplayName("deve bloquear em modo estrito quando score exceder o limite")
    void deveBloquearEmModoEstritoQuandoScoreExcederOLimite() {
        properties.setModoObservacao(false);
        properties.setHabilitado(true);

        AvaliacaoSegurancaAplicativoService service = new AvaliacaoSegurancaAplicativoService(
                properties,
                atestacaoAppProperties,
                auditoriaService
        );

        SegurancaAplicativoApiRequest request = new SegurancaAplicativoApiRequest(
                "IOS",
                "APPLE_APP_ATTEST",
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                List.of("debugger_detectado_cliente"),
                95,
                null,
                "com.eickrono.thimisu.invalido",
                "TEAM99999",
                null
        );

        FluxoPublicoException exception =
                (FluxoPublicoException) org.assertj.core.api.Assertions.catchThrowable(() -> service.avaliar(
                        "cadastro",
                        "eickrono-thimisu-app",
                        "IOS",
                        request,
                        "novo-usuario"
                ));

        assertThat(exception).isNotNull();
        assertThat(exception.getCodigo()).isEqualTo("seguranca_aplicativo_reprovada");
        assertThat(exception.getDetalhes()).containsKey("scoreRisco");

        verify(auditoriaService).registrarEvento(
                eq("SEGURANCA_APP_CADASTRO"),
                eq("novo-usuario"),
                contains("scoreCalculado=")
        );
    }
}
