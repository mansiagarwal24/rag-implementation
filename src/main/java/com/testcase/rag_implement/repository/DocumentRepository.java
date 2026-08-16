package com.testcase.rag_implement.repository;

import com.testcase.rag_implement.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * All finders are tenant-scoped by signature. There is intentionally no plain
 * {@code findById} exposed to services, so a caller cannot accidentally read
 * another tenant's document.
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Page<DocumentEntity> findByTenantId(String tenantId, Pageable pageable);

    Optional<DocumentEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<DocumentEntity> findByTenantIdAndContentHash(String tenantId, String contentHash);

    boolean existsByIdAndTenantId(UUID id, String tenantId);
}
