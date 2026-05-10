package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório de vínculos sociais.
 */
public interface VinculoSocialRepositorio extends JpaRepository<VinculoSocial, Long> {

    List<VinculoSocial> findByPerfil(PerfilIdentidade perfil);
}
