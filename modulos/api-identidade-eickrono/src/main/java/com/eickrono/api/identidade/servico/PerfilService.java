package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dto.PerfilDto;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações relacionadas ao perfil do usuário autenticado.
 */
@Service
public class PerfilService {

    private final PerfilIdentidadeRepositorio perfilRepositorio;

    public PerfilService(PerfilIdentidadeRepositorio perfilRepositorio) {
        this.perfilRepositorio = perfilRepositorio;
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
}
