package com.testcase.rag_implement.repository;

import com.testcase.rag_implement.entity.MessageSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageSourceRepository extends JpaRepository<MessageSourceEntity, UUID> {
}
