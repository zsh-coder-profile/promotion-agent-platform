package com.example.agentscope.workflow.sqlagent.memory;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentScopeToolUsageMemory implements ToolUsageMemory {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Logger log = LoggerFactory.getLogger(AgentScopeToolUsageMemory.class);

    private final Knowledge knowledge;
    private final ObjectMapper objectMapper;

    public AgentScopeToolUsageMemory(Knowledge knowledge, ObjectMapper objectMapper) {
        this.knowledge = knowledge;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ToolUsageMemorySearchResult> searchSimilarUsage(
            String question,
            SqlAccessContext accessContext,
            int limit,
            double similarityThreshold) {
        log.info(
                "Searching SQL tool usage memory, question={}, tenantId={}, userId={}, limit={}, similarityThreshold={}",
                preview(question),
                accessContext == null ? null : accessContext.tenantId(),
                accessContext == null ? null : accessContext.userId(),
                limit,
                similarityThreshold);
        RetrieveConfig config = RetrieveConfig.builder()
                .limit(limit)
                .scoreThreshold(similarityThreshold)
                .build();
        try {
            List<Document> documents = knowledge.retrieve(question, config).blockOptional().orElse(List.of());
            List<ToolUsageMemorySearchResult> results = documents.stream()
                .filter(document -> matchesAccessScope(document, accessContext))
                .map(this::toSearchResult)
                .toList();
            log.info(
                    "SQL tool usage memory search completed, question={}, retrievedDocuments={}, filteredResults={}",
                    preview(question),
                    documents.size(),
                    results.size());
            return results;
        } catch (RuntimeException e) {
            String errorMessage = buildHelpfulErrorMessage("search", e);
            log.error(
                    "SQL tool usage memory search failed, question={}, tenantId={}, userId={}, limit={}, similarityThreshold={}, error={}",
                    preview(question),
                    accessContext == null ? null : accessContext.tenantId(),
                    accessContext == null ? null : accessContext.userId(),
                    limit,
                    similarityThreshold,
                    errorMessage,
                    e);
            throw new IllegalStateException(
                    "Failed to search SQL tool usage memory. question=%s, tenantId=%s, userId=%s, limit=%d, similarityThreshold=%s, cause=%s"
                            .formatted(
                                    preview(question),
                                    accessContext == null ? null : accessContext.tenantId(),
                                    accessContext == null ? null : accessContext.userId(),
                                    limit,
                                    similarityThreshold,
                                    errorMessage),
                    e);
        }
    }

    @Override
    public void saveSuccessfulUsage(
            String question,
            SqlAccessContext accessContext,
            List<RecordedToolUsage> toolUsages,
            String executedSql) {
        if (toolUsages == null || toolUsages.isEmpty()) {
            log.info(
                    "Skip saving SQL tool usage memory because no tool usages were recorded, question={}, tenantId={}, userId={}",
                    preview(question),
                    accessContext == null ? null : accessContext.tenantId(),
                    accessContext == null ? null : accessContext.userId());
            return;
        }

        log.info(
                "Saving SQL tool usage memory, question={}, tenantId={}, userId={}, toolUsageCount={}",
                preview(question),
                accessContext == null ? null : accessContext.tenantId(),
                accessContext == null ? null : accessContext.userId(),
                toolUsages.size());
        String requestId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Document> documents = new ArrayList<>();
        for (RecordedToolUsage toolUsage : toolUsages) {
            Map<String, Object> payload = Map.of(
                    "question", question,
                    "toolName", toolUsage.toolName(),
                    "toolArgsJson", writeJson(toolUsage.args()),
                    "success", true,
                    "tenantId", accessContext == null ? null : accessContext.tenantId(),
                    "userId", accessContext == null ? null : accessContext.userId(),
                    "userRole", accessContext == null ? null : accessContext.role(),
                    "requestId", requestId,
                    "executedSql", executedSql,
                    "createdAt", TIMESTAMP_FORMATTER.format(now));
            DocumentMetadata metadata = new DocumentMetadata(
                    TextBlock.builder().text(question).build(),
                    requestId,
                    UUID.randomUUID().toString(),
                    payload);
            documents.add(new Document(metadata));
        }
        try {
            knowledge.addDocuments(documents).block();
            log.info(
                    "Saved SQL tool usage memory successfully, requestId={}, question={}, tenantId={}, toolUsageCount={}",
                    requestId,
                    preview(question),
                    accessContext == null ? null : accessContext.tenantId(),
                    toolUsages.size());
        } catch (RuntimeException e) {
            String errorMessage = buildHelpfulErrorMessage("save", e);
            log.error(
                    "Failed to save SQL tool usage memory, requestId={}, question={}, tenantId={}, userId={}, toolUsageCount={}, error={}",
                    requestId,
                    preview(question),
                    accessContext == null ? null : accessContext.tenantId(),
                    accessContext == null ? null : accessContext.userId(),
                    toolUsages.size(),
                    errorMessage,
                    e);
            throw new IllegalStateException(
                    "Failed to save SQL tool usage memory. requestId=%s, question=%s, tenantId=%s, userId=%s, toolUsageCount=%d, cause=%s"
                            .formatted(
                                    requestId,
                                    preview(question),
                                    accessContext == null ? null : accessContext.tenantId(),
                                    accessContext == null ? null : accessContext.userId(),
                                    toolUsages.size(),
                                    errorMessage),
                    e);
        }
    }

    private static String buildHelpfulErrorMessage(String operation, RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getName();
        }
        if (message.contains("dimension mismatch")) {
            return "%s. Detected pgvector/embedding dimension mismatch during %s. "
                    .formatted(message, operation)
                    + "Please align `workflow.sql.memory.embedding.dimensions` with the embedding model output, "
                    + "then recreate `sql_tool_usage_memory` in the pgvector database if it was created with the wrong vector size.";
        }
        return message;
    }

    private boolean matchesAccessScope(Document document, SqlAccessContext accessContext) {
        if (accessContext == null || accessContext.isAdmin()) {
            return true;
        }
        String tenantId = stringValue(document.getPayloadValue("tenantId"));
        return tenantId != null && tenantId.equals(accessContext.tenantId());
    }

    private ToolUsageMemorySearchResult toSearchResult(Document document) {
        return new ToolUsageMemorySearchResult(
                stringValue(document.getPayloadValue("question")),
                stringValue(document.getPayloadValue("toolName")),
                readJsonMap(stringValue(document.getPayloadValue("toolArgsJson"))),
                document.getScore() == null ? 0.0d : document.getScore(),
                parseCreatedAt(stringValue(document.getPayloadValue("createdAt"))));
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse tool usage memory json.", e);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tool usage memory json.", e);
        }
    }

    private static OffsetDateTime parseCreatedAt(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value, TIMESTAMP_FORMATTER);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }
}
