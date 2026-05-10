package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.DesafioAtestacaoApp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesafioAtestacaoAppRepositorio extends JpaRepository<DesafioAtestacaoApp, Long> {

    Optional<DesafioAtestacaoApp> findByIdentificadorDesafio(String identificadorDesafio);
}
