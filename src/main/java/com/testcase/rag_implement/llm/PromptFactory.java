package com.testcase.rag_implement.llm;

import com.testcase.rag_implement.entity.MessageEntity;
import com.testcase.rag_implement.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the strictly-grounded system prompt and the user prompt. Only retrieved chunks
 * enter the context — never full documents. Each chunk is labelled with a source id so the
 * answer is traceable back to a citation.
 */
@Component
public class PromptFactory {

    private static final String SYSTEM_PROMPT = """
            You are a document-grounded assistant for a school administration system.
            Answer ONLY using the supplied context passages. Do not use outside knowledge.
            Do not invent facts, figures, dates, or policies.
            If the context does not contain enough information to answer, reply that the
            information is not available in the supplied documents.
            Every factual statement you make must be supported by the supplied context.
            When helpful, refer to sources by their [Source N] label.

            Reading tabular data carefully:
            - Fee tables often list several columns such as "Per Month" and "Per Quarter"
              (Per QTR). Match the value to the column and to the exact class/grade asked
              about. Do not confuse a per-quarter figure with a per-month figure.
            - If the class, period, or unit for a number is ambiguous in the context, say
              what you can support and note the ambiguity rather than guessing.
            - Quote the number exactly as it appears and state its unit and the class it
              applies to.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(String question, List<RetrievedChunk> chunks, List<MessageEntity> history) {
        StringBuilder sb = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            sb.append("Conversation so far:\n");
            for (MessageEntity m : history) {
                sb.append(m.getRole()).append(": ").append(m.getContent()).append('\n');
            }
            sb.append('\n');
        }

        sb.append("Context passages:\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append("[Source ").append(i + 1).append("] ")
                    .append("(document: ").append(c.documentTitle())
                    .append(", page: ").append(c.pageNumber() == null ? "n/a" : c.pageNumber())
                    .append(")\n")
                    .append(c.content())
                    .append("\n\n");
        }

        sb.append("Question: ").append(question).append('\n');
        sb.append("Answer using only the context above.");
        return sb.toString();
    }
}
