package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.PerfilDto;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações relacionadas ao perfil do usuário autenticado.
 */
@Service
public class PerfilService {

    private final PerfilIdentidadeRepositorio perfilRepositorio;
    private final ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

    public PerfilService(PerfilIdentidadeRepositorio perfilRepositorio,
                         ProvisionamentoIdentidadeService provisionamentoIdentidadeService) {
        this.perfilRepositorio = perfilRepositorio;
        this.provisionamentoIdentidadeService = provisionamentoIdentidadeService;
    }

    @Transactional(readOnly = true)
    public Optional<PerfilDto> buscarPorSub(String sub) {
        return perfilRepositorio.findBySub(sub)
                .map(perfil -> new PerfilDto(
                        perfil.getSub(),
                        perfil.getEmail(),
                        perfil.getNome(),
                        perfil.getPerfis(),
                        perfil.getPapeis(),
                        perfil.getAtualizadoEm()));
    }

    @Transactional
    public PerfilDto buscarOuProvisionar(Jwt jwt) {
        provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        return buscarPorSub(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("Perfil não encontrado após provisionamento controlado"));
    }
}
