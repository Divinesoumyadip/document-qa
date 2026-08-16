package com.schooldesk.docqa.chat;

import java.util.List;

import com.schooldesk.docqa.retrieval.RetrievedChunk;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are a school office assistant. Answer questions using ONLY the \
            context provided below.

            Rules you must follow without exception:
            1. If the context does not contain the answer, reply exactly: \
            "I could not find that in the available documents."
            2. Never use knowledge outside the supplied context.
            3. Every factual claim must come from the context.
            4. Quote figures, dates and amounts exactly as they appear.
            5. Be concise. Two or three sentences is usually enough.
            """;

    public String buildUserPrompt(String question, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUESTION: ").append(question).append("\n\n");
        sb.append("CONTEXT:\n");

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            sb.append("[Source ").append(i + 1).append(" - ")
              .append(chunk.documentTitle());
            if (chunk.pageNumber() != null) {
                sb.append(", page ").append(chunk.pageNumber());
            }
            sb.append("]\n").append(chunk.content()).append("\n\n");
        }
        return sb.toString();
    }
}
