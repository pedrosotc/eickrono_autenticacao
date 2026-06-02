package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.ExclusaoCadastroProdutoDryRunService;
import com.eickrono.api.identidade.aplicacao.servico.ExclusaoCadastroProdutoExecucaoService;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiResposta;
import jakarta.validation.Valid;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interna/usuarios/exclusoes")
public class ExclusaoCadastroProdutoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusaoCadastroProdutoController.class);

    private final ExclusaoCadastroProdutoDryRunService dryRunService;
    private final ExclusaoCadastroProdutoExecucaoService execucaoService;

    public ExclusaoCadastroProdutoController(final ExclusaoCadastroProdutoDryRunService dryRunService,
                                             final ExclusaoCadastroProdutoExecucaoService execucaoService) {
        this.dryRunService = Objects.requireNonNull(dryRunService, "dryRunService e obrigatorio");
        this.execucaoService = Objects.requireNonNull(execucaoService, "execucaoService e obrigatorio");
    }

    @PostMapping
    public ResponseEntity<ExclusaoCadastroProdutoApiResposta> excluirCadastroProduto(
            @Valid @RequestBody final ExclusaoCadastroProdutoApiRequest requisicao) {
        LOGGER.info(
                "exclusao_cadastro_produto_requisicao_recebida dryRun={} correlacaoId={} produto={} usuarioPublicoProduto={} perfilProdutoIdPresente={}",
                requisicao.dryRun(),
                requisicao.correlacaoId(),
                requisicao.produto(),
                requisicao.usuarioPublicoProduto(),
                requisicao.perfilProdutoId() != null && !requisicao.perfilProdutoId().isBlank()
        );
        if (!requisicao.dryRun()) {
            ExclusaoCadastroProdutoApiResposta resposta = execucaoService.executar(requisicao);
            LOGGER.info(
                    "exclusao_cadastro_produto_requisicao_concluida dryRun=false correlacaoId={} bloqueios={} acoes={} preservados={}",
                    resposta.correlacaoId(),
                    resposta.bloqueios().size(),
                    resposta.acoes().size(),
                    resposta.preservados().size()
            );
            return ResponseEntity.ok(resposta);
        }
        ExclusaoCadastroProdutoApiResposta resposta = dryRunService.simular(requisicao);
        LOGGER.info(
                "exclusao_cadastro_produto_requisicao_concluida dryRun=true correlacaoId={} bloqueios={} acoes={} preservados={}",
                resposta.correlacaoId(),
                resposta.bloqueios().size(),
                resposta.acoes().size(),
                resposta.preservados().size()
        );
        return ResponseEntity.ok(resposta);
    }
}
