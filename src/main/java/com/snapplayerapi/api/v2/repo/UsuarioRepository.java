package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.UsuarioEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for phase-1 user resolution by e-mail.
 */
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    /**
     * E-mail is treated as the stable identity key before authentication exists.
     */
    Optional<UsuarioEntity> findByEmail(String email);
}
