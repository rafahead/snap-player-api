package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.SnapSubjectAttrEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for flattened subject attributes.
 */
public interface SnapSubjectAttrRepository extends JpaRepository<SnapSubjectAttrEntity, Long> {
    /**
     * Utility method reserved for future snap update/delete flows.
     */
    void deleteBySnapId(UUID snapId);
}
