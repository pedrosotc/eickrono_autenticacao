package com.eickrono.api.contas.servico;

import com.eickrono.api.contas.dominio.modelo.Conta;
import com.eickrono.api.contas.dominio.repositorio.ContaRepositorio;
import com.eickrono.api.contas.dto.ContaResumoDto;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviços de leitura de contas.
 */
@Service
public class ContaService {

    private final ContaRepositorio contaRepositorio;
    private final AuditoriaContasService auditoriaContasService;

    public ContaService(ContaRepositorio contaRepositorio, AuditoriaContasService auditoriaContasService) {
        this.contaRepositorio = contaRepositorio;
        this.auditoriaContasService = auditoriaContasService;
    }

    @Transactional(readOnly = true)
    public List<ContaResumoDto> listarPorCliente(String clienteId) {
        return contaRepositorio.findByClienteId(clienteId)
                .stream()
                .map(this::mapear)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ContaResumoDto> buscarPorId(Long id, String clienteId) {
        return contaRepositorio.findById(id)
                .filter(conta -> conta.getClienteId().equals(clienteId))
                .map(conta -> {
                    auditoriaContasService.registrarAcesso(clienteId, "/contas/" + id, "Consulta de conta");
                    return mapear(conta);
                });
    }

    private ContaResumoDto mapear(Conta conta) {
        return new ContaResumoDto(conta.getId(), conta.getNumero(), conta.getSaldo(), conta.getAtualizadaEm());
    }
}
