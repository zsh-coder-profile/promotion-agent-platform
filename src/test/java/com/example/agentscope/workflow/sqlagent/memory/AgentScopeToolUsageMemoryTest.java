package com.example.agentscope.workflow.sqlagent.memory;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Mono;

class AgentScopeToolUsageMemoryTest {

    @Test
    void searchUsesKnowledgeRetrieveAndMapsPayload() {
        Knowledge knowledge = mock(Knowledge.class);
        DocumentMetadata metadata = new DocumentMetadata(
                TextBlock.builder().text("查询订单").build(),
                "doc-1",
                "chunk-1",
                Map.of(
                        "question", "查询订单",
                        "toolName", "sql_db_schema",
                        "toolArgsJson", "{\"tableNames\":\"orders\"}",
                        "tenantId", "tenant-a",
                        "createdAt", OffsetDateTime.now().toString()));
        Document document = new Document(metadata);
        document.setScore(0.88d);
        when(knowledge.retrieve(eq("订单查询"), any(RetrieveConfig.class))).thenReturn(Mono.just(List.of(document)));

        AgentScopeToolUsageMemory memory = new AgentScopeToolUsageMemory(knowledge, new ObjectMapper());

        List<ToolUsageMemorySearchResult> results = memory.searchSimilarUsage(
                "订单查询",
                SqlAccessContext.tenantUser("user-a", "tenant-a"),
                3,
                0.7d);

        assertEquals(1, results.size());
        assertEquals("sql_db_schema", results.get(0).toolName());
        assertEquals(0.88d, results.get(0).similarity());
        assertEquals("orders", results.get(0).args().get("tableNames"));
    }

    @Test
    void saveAddsDocumentsThroughKnowledge() {
        Knowledge knowledge = mock(Knowledge.class);
        when(knowledge.addDocuments(any())).thenReturn(Mono.empty());
        AgentScopeToolUsageMemory memory = new AgentScopeToolUsageMemory(knowledge, new ObjectMapper());

        memory.saveSuccessfulUsage(
                "查询订单",
                SqlAccessContext.tenantUser("user-a", "tenant-a"),
                List.of(new RecordedToolUsage("sql_db_schema", Map.of("tableNames", "orders"))),
                "SELECT * FROM orders");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(knowledge).addDocuments(docsCaptor.capture());
        assertEquals(1, docsCaptor.getValue().size());
        Document stored = docsCaptor.getValue().get(0);
        assertEquals("查询订单", stored.getMetadata().getPayloadValue("question"));
        assertEquals("sql_db_schema", stored.getMetadata().getPayloadValue("toolName"));
        assertTrue(stored.getMetadata().getPayloadValue("toolArgsJson").toString().contains("orders"));
    }
}
