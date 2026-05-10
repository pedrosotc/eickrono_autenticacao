package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.NovaPendenciaIntegracaoProduto;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import com.eickrono.api.identidade.dominio.repositorio.PendenciaIntegracaoProdutoRepositorio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistradorPendenciaIntegracaoProdutoServiceTest {

    @Mock
    private PendenciaIntegracaoProdutoRepositorio repositorio;

    @Mock
    private SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;

    @Mock
    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<NovaPendenciaIntegracaoProduto> pendenciaCaptor;

    private RegistradorPendenciaIntegracaoProdutoService service;

    @BeforeEach
    void setUp() {
        service = new RegistradorPendenciaIntegracaoProdutoService(
                repositorio,
                sincronizacaoModeloMultiappService,
                objectMapper,
                Clock.fixed(Instant.parse("2026-05-03T13:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("deve registrar pendencia de provisionamento do perfil do sistema")
    void deveRegistrarPendenciaDeProvisionamentoDoPerfilDoSistema() throws Exception {
        CadastroConta cadastroConta = new CadastroConta(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sub-ana",
                TipoPessoaCadastro.FISICA,
                "Ana Souza",
                null,
                "ana.souza",
                null,
                null,
                null,
                "ana@eickrono.com",
                "+5511999999999",
                CanalValidacaoTelefoneCadastro.SMS,
                "hash-codigo",
                OffsetDateTime.parse("2026-05-03T12:00:00Z"),
                OffsetDateTime.parse("2026-05-03T21:00:00Z"),
                "eickrono-thimisu-app",
                "127.0.0.1",
                "JUnit",
                OffsetDateTime.parse("2026-05-03T12:00:00Z"),
                OffsetDateTime.parse("2026-05-03T12:00:00Z")
        );
        when(sincronizacaoModeloMultiappService.assegurarClienteEcossistemaParaSistemaSolicitante(
                "eickrono-thimisu-app",
                OffsetDateTime.parse("2026-05-03T13:00:00Z")
        )).thenReturn(9L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");

        service.registrarProvisionamentoPerfilSistema(
                cadastroConta,
                77L,
                "PROVISIONAMENTO_PERFIL_SISTEMA_HTTP_502",
                "Falha toleravel"
        );

        verify(repositorio).registrarOuAtualizar(pendenciaCaptor.capture());
        NovaPendenciaIntegracaoProduto pendencia = pendenciaCaptor.getValue();
        assertThat(pendencia.clienteEcossistemaId()).isEqualTo(9L);
        assertThat(pendencia.tipoOperacao()).isEqualTo("PROVISIONAR_PERFIL_SISTEMA");
        assertThat(pendencia.uriEndpoint()).isEqualTo("/api/interna/perfis-sistema/provisionamentos");
        assertThat(pendencia.metodoHttp()).isEqualTo("POST");
        assertThat(pendencia.payloadJson()).isEqualTo("{\"ok\":true}");
        assertThat(pendencia.idempotencyKey())
                .isEqualTo("cadastro:11111111-1111-1111-1111-111111111111:provisionar-perfil-sistema");
        assertThat(pendencia.cadastroId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(pendencia.pessoaIdCentral()).isEqualTo(77L);
        assertThat(pendencia.identificadorPublicoSistema()).isEqualTo("ana.souza");
        assertThat(pendencia.statusPendencia()).isEqualTo("PENDENTE_ENVIO");
        assertThat(pendencia.codigoUltimoErro()).isEqualTo("PROVISIONAMENTO_PERFIL_SISTEMA_HTTP_502");
        assertThat(pendencia.mensagemUltimoErro()).isEqualTo("Falha toleravel");
    }
}
