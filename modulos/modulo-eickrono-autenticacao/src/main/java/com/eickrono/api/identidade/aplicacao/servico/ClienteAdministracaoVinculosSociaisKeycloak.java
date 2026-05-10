package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import java.util.List;

/**
 * Cliente administrativo para consulta e remoção de identidades federadas no Keycloak.
 */
public interface ClienteAdministracaoVinculosSociaisKeycloak {

    List<IdentidadeFederadaKeycloak> listarIdentidadesFederadas(String subjectRemoto);

    void removerIdentidadeFederada(String subjectRemoto, ProvedorVinculoSocial provedor);
}
