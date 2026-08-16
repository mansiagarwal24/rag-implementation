package com.testcase.rag_implement.repository;

import com.testcase.rag_implement.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByIdAndTenantId(UUID id, String tenantId);
}
