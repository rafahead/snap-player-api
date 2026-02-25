package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.AssinaturaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for assinatura lookups.
 */
public interface AssinaturaRepository extends JpaRepository<AssinaturaEntity, Long> {
    /**
     * Finds a tenant by stable business code (used by Entrega 1 fixed default assinatura).
     */
    Optional<AssinaturaEntity> findByCodigo(String codigo);

    /**
     * Finds a tenant by API token (used by the optional token-auth preparation in Entrega 3).
     */
    Optional<AssinaturaEntity> findByApiToken(String apiToken);
}
