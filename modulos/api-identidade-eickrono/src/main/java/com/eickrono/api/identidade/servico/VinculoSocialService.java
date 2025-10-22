package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.dto.CriarVinculoSocialRequisicao;
import com.eickrono.api.identidade.dto.VinculoSocialDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço para manutenção de vínculos sociais.
 */
@Service
public class VinculoSocialService {

    private final PerfilIdentidadeRepositorio perfilRepositorio;
    private final VinculoSocialRepositorio vinculoRepositorio;
    private final AuditoriaService auditoriaService;

    public VinculoSocialService(PerfilIdentidadeRepositorio perfilRepositorio,
                                VinculoSocialRepositorio vinculoRepositorio,
                                AuditoriaService auditoriaService) {
        this.perfilRepositorio = perfilRepositorio;
        this.vinculoRepositorio = vinculoRepositorio;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<VinculoSocialDto> listar(String sub) {
        PerfilIdentidade perfil = localizarPerfil(sub);
        return vinculoRepositorio.findByPerfil(perfil)
                .stream()
                .map(vinculo -> new VinculoSocialDto(
                        vinculo.getId(),
                        vinculo.getProvedor(),
                        vinculo.getIdentificador(),
                        vinculo.getVinculadoEm()))
                .collect(Collectors.toList());
    }

    @Transactional
    public VinculoSocialDto criar(String sub, CriarVinculoSocialRequisicao requisicao) {
        PerfilIdentidade perfil = localizarPerfil(sub);
        VinculoSocial novoVinculo = new VinculoSocial(
                perfil,
                requisicao.provedor(),
                requisicao.identificador(),
                OffsetDateTime.now());
        VinculoSocial salvo = vinculoRepositorio.save(novoVinculo);
        auditoriaService.registrarEvento("VINCULO_SOCIAL_CRIADO", sub,
                "Provedor=" + requisicao.provedor());
        return new VinculoSocialDto(
                salvo.getId(),
                salvo.getProvedor(),
                salvo.getIdentificador(),
                salvo.getVinculadoEm());
    }

    private PerfilIdentidade localizarPerfil(String sub) {
        return perfilRepositorio.findBySub(sub)
                .orElseThrow(() -> new IllegalArgumentException("Perfil não encontrado para o usuário informado"));
    }
}
