package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.NovaPendenciaIntegracaoProduto;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.repositorio.PendenciaIntegracaoProdutoRepositorio;
import com.eickrono.api.identidade.infraestrutura.integracao.ProvisionamentoPerfilSistemaProdutoRequestPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegistradorPendenciaIntegracaoProdutoService {

    private static final String TIPO_OPERACAO_PROVISIONAR_PERFIL_SISTEMA = "PROVISIONAR_PERFIL_SISTEMA";
    private static final String CAMINHO_PROVISIONAMENTO_PERFIL_SISTEMA = "/api/interna/perfis-sistema/provisionamentos";
    private static final String METODO_HTTP_POST = "POST";
    private static final String STATUS_PENDENTE_ENVIO = "PENDENTE_ENVIO";
    private static final String VERSAO_CONTRATO_ATUAL = "v1";

    private final PendenciaIntegracaoProdutoRepositorio repositorio;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RegistradorPendenciaIntegracaoProdutoService(
            final PendenciaIntegracaoProdutoRepositorio repositorio,
            final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService,
            final ObjectMapper objectMapper,
            final Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio e obrigatorio");
        this.sincronizacaoModeloMultiappService = Objects.requireNonNull(
                sincronizacaoModeloMultiappService,
                "sincronizacaoModeloMultiappService e obrigatorio"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio");
    }

    public void registrarProvisionamentoPerfilSistema(final CadastroConta cadastroConta,
                                                      final Long pessoaIdCentral,
                                                      final String codigoUltimoErro,
                                                      final String mensagemUltimoErro) {
        Objects.requireNonNull(cadastroConta, "cadastroConta e obrigatorio");
        Objects.requireNonNull(pessoaIdCentral, "pessoaIdCentral e obrigatorio");
        OffsetDateTime agora = OffsetDateTime.now(clock);
        long clienteEcossistemaId = sincronizacaoModeloMultiappService
                .assegurarClienteEcossistemaParaSistemaSolicitante(cadastroConta.getSistemaSolicitante(), agora);
        repositorio.registrarOuAtualizar(new NovaPendenciaIntegracaoProduto(
                UUID.randomUUID(),
                clienteEcossistemaId,
                TIPO_OPERACAO_PROVISIONAR_PERFIL_SISTEMA,
                CAMINHO_PROVISIONAMENTO_PERFIL_SISTEMA,
                METODO_HTTP_POST,
                serializarPayload(cadastroConta, pessoaIdCentral),
                "cadastro:" + cadastroConta.getCadastroId() + ":provisionar-perfil-sistema",
                VERSAO_CONTRATO_ATUAL,
                cadastroConta.getCadastroId(),
                pessoaIdCentral,
                cadastroConta.getPerfilSistemaId(),
                cadastroConta.getUsuario(),
                STATUS_PENDENTE_ENVIO,
                agora,
                codigoUltimoErro,
                mensagemUltimoErro,
                agora,
                agora
        ));
    }

    private String serializarPayload(final CadastroConta cadastroConta, final Long pessoaIdCentral) {
        try {
            return objectMapper.writeValueAsString(
                    ProvisionamentoPerfilSistemaProdutoRequestPayload.fromCadastro(cadastroConta, pessoaIdCentral)
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nao foi possivel serializar a pendencia de provisionamento do produto.", ex);
        }
    }
}
