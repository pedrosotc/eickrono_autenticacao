package com.eickrono.api.contas.servico;

import com.eickrono.api.contas.dominio.modelo.Conta;
import com.eickrono.api.contas.dominio.modelo.Transacao;
import com.eickrono.api.contas.dominio.repositorio.ContaRepositorio;
import com.eickrono.api.contas.dominio.repositorio.TransacaoRepositorio;
import com.eickrono.api.contas.dto.TransacaoDto;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviços relacionados às transações.
 */
@Service
public class TransacaoService {

    private final ContaRepositorio contaRepositorio;
    private final TransacaoRepositorio transacaoRepositorio;
    private final AuditoriaContasService auditoriaContasService;

    public TransacaoService(ContaRepositorio contaRepositorio,
                            TransacaoRepositorio transacaoRepositorio,
                            AuditoriaContasService auditoriaContasService) {
        this.contaRepositorio = contaRepositorio;
        this.transacaoRepositorio = transacaoRepositorio;
        this.auditoriaContasService = auditoriaContasService;
    }

    @Transactional(readOnly = true)
    public List<TransacaoDto> listarPorConta(Long contaId, String clienteId) {
        Conta conta = contaRepositorio.findById(contaId)
                .filter(c -> c.getClienteId().equals(clienteId))
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada para o cliente informado"));
        auditoriaContasService.registrarAcesso(clienteId, "/transacoes", "Listagem de transações");
        return transacaoRepositorio.findByContaOrderByEfetivadaEmDesc(conta)
                .stream()
                .map(this::mapear)
                .collect(Collectors.toList());
    }

    private TransacaoDto mapear(Transacao transacao) {
        return new TransacaoDto(
                transacao.getId(),
                transacao.getTipo().name(),
                transacao.getValor(),
                transacao.getEfetivadaEm(),
                transacao.getDescricao());
    }
}
