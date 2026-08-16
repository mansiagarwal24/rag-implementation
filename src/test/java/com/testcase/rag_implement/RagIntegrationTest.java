package com.testcase.rag_implement;

import com.testcase.rag_implement.dto.ChatDtos;
import com.testcase.rag_implement.dto.DocumentDtos;
import com.testcase.rag_implement.entity.DocumentStatus;
import com.testcase.rag_implement.repository.ChunkRepository;
import com.testcase.rag_implement.repository.DocumentRepository;
import com.testcase.rag_implement.retrieval.RetrievalService;
import com.testcase.rag_implement.retrieval.RetrievedChunk;
import com.testcase.rag_implement.service.ChatService;
import com.testcase.rag_implement.service.DocumentService;
import com.testcase.rag_implement.support.AbstractIntegrationTest;
import com.testcase.rag_implement.support.StubModels;
import com.testcase.rag_implement.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

class RagIntegrationTest extends AbstractIntegrationTest {

    @Autowired DocumentService documentService;
    @Autowired ChatService chatService;
    @Autowired RetrievalService retrievalService;
    @Autowired DocumentRepository documentRepository;
    @Autowired ChunkRepository chunkRepository;

    private static final String FEES_TEXT =
            "The late fee for term two is five hundred rupees. Fees must be paid before the tenth.";
    private static final String TRANSPORT_TEXT =
            "The school bus route covers the north district. Transport charges are billed quarterly.";

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        StubModels.COMPLETE_CALLS.set(0);
    }

    private UUID uploadAndWait(String tenant, String filename, String content, String category) {
        TenantContext.set(tenant);
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        DocumentDtos.UploadResponse response = documentService.upload(file, filename, category);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            TenantContext.set(tenant);
            assertThat(documentService.get(response.documentId()).status()).isEqualTo(DocumentStatus.READY);
        });
        return response.documentId();
    }

    @Test
    void uploadIsAsyncAndReachesReadyWithChunks() {
        UUID id = uploadAndWait("tenant-a", "fees.txt", FEES_TEXT, "FEES");
        assertThat(chunkRepository.countByDocumentId(id)).isGreaterThan(0);
    }

    @Test
    void reuploadingSameContentIsIdempotent() {
        UUID first = uploadAndWait("tenant-a", "fees.txt", FEES_TEXT, "FEES");
        long chunksAfterFirst = chunkRepository.countByDocumentId(first);

        TenantContext.set("tenant-a");
        MockMultipartFile duplicate = new MockMultipartFile("file", "fees.txt", "text/plain",
                FEES_TEXT.getBytes(StandardCharsets.UTF_8));
        DocumentDtos.UploadResponse second = documentService.upload(duplicate, "fees.txt", "FEES");

        assertThat(second.documentId()).isEqualTo(first);
        assertThat(chunkRepository.countByDocumentId(first)).isEqualTo(chunksAfterFirst);
    }

    @Test
    void tenantCannotRetrieveAnotherTenantsChunks() {
        UUID feesDoc = uploadAndWait("tenant-a", "fees.txt", FEES_TEXT, "FEES");
        uploadAndWait("tenant-b", "transport.txt", TRANSPORT_TEXT, "TRANSPORT");

        // tenant-a asks something that matches tenant-b's transport document.
        List<RetrievedChunk> results = retrievalService.retrieve("tenant-a", null,
                "What are the school bus transport charges?");

        // Nothing returned may belong to tenant-b. tenant-a only owns the fees doc.
        assertThat(results).allMatch(c -> c.documentId().equals(feesDoc));
    }

    @Test
    void refusalPathDoesNotCallTheLlm() {
        uploadAndWait("tenant-a", "fees.txt", FEES_TEXT, "FEES");
        TenantContext.set("tenant-a");

        ChatDtos.ChatResponse response = chatService.chat(new ChatDtos.ChatRequest(
                null, "Explain quantum entanglement in astrophysics satellites", null));

        assertThat(response.grounded()).isFalse();
        assertThat(response.sources()).isEmpty();
        assertThat(StubModels.COMPLETE_CALLS.get()).isZero();
    }

    @Test
    void groundedQuestionReturnsAnswerWithCitationsAndSupportsFollowUp() {
        uploadAndWait("tenant-a", "fees.txt", FEES_TEXT, "FEES");
        TenantContext.set("tenant-a");

        ChatDtos.ChatResponse first = chatService.chat(new ChatDtos.ChatRequest(
                null, "What is the late fee for term two?", "FEES"));

        assertThat(first.grounded()).isTrue();
        assertThat(first.sources()).isNotEmpty();
        assertThat(first.sources().get(0).pageNumber()).isEqualTo(1);
        assertThat(first.conversationId()).isNotNull();
        assertThat(StubModels.COMPLETE_CALLS.get()).isEqualTo(1);

        // Follow-up reuses the same conversation.
        ChatDtos.ChatResponse followUp = chatService.chat(new ChatDtos.ChatRequest(
                first.conversationId(), "And when must fees be paid?", "FEES"));
        assertThat(followUp.conversationId()).isEqualTo(first.conversationId());
    }

    @Test
    void deletedDocumentIsNoLongerRetrievable() {
        UUID id = uploadAndWait("tenant-a", "fees.txt", FEES_TEXT, "FEES");
        TenantContext.set("tenant-a");
        assertThat(retrievalService.retrieve("tenant-a", null, "late fee term two rupees")).isNotEmpty();

        documentService.delete(id);

        assertThat(retrievalService.retrieve("tenant-a", null, "late fee term two rupees")).isEmpty();
        assertThat(chunkRepository.countByDocumentId(id)).isZero();
    }
}
