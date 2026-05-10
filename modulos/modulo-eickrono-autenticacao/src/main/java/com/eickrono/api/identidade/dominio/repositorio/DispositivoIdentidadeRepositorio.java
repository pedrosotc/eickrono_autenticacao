package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoIdentidadeRepositorio extends JpaRepository<DispositivoIdentidade, Long> {

    Optional<DispositivoIdentidade> findByUsuarioSubAndFingerprint(String usuarioSub, String fingerprint);
}
