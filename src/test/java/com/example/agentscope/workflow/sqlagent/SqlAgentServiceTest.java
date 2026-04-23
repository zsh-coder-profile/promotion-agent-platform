package com.example.agentscope.workflow.sqlagent;

import com.example.agentscope.workflow.sqlagent.memory.RecordedToolUsage;
import com.example.agentscope.workflow.sqlagent.memory.SqlToolUsageRecorder;
import com.example.agentscope.workflow.sqlagent.memory.ToolUsageMemory;
import com.example.agentscope.workflow.sqlagent.memory.ToolUsageMemorySearchResult;
import com.example.agentscope.workflow.sqlagent.tools.DatabaseDialect;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlAgentServiceTest {

    @Test
    void runExecutesSqlLocallyFromJsonResponse() {
        ReActAgent agent = mock(ReActAgent.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqlTools sqlTools = new SqlTools(jdbcTemplate, DatabaseDialect.POSTGRESQL);
        Msg response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .textContent("{\"sql\":\"SELECT genre, avg_ms FROM genre_stats LIMIT 5\",\"debug_summary\":\"命中 genre_stats 表，按租户过滤并保留 5 条样本。\"}")
                        .build();
        List<Map<String, Object>> rows = List.of(Map.of("genre", "Jazz", "avg_ms", 123));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(rows);
        ToolUsageMemory memory = mock(ToolUsageMemory.class);
        SqlToolUsageRecorder recorder = new SqlToolUsageRecorder();
        when(agent.call(any(Msg.class))).thenAnswer(invocation -> {
            recorder.record("sql_db_list_tables", Map.of("ignored", ""));
            recorder.record("sql_db_schema", Map.of("tableNames", "genre_stats"));
            return Mono.just(response);
        });

        SqlAgentService service = new SqlAgentService(agent, sqlTools, memory, recorder);

        SqlAgentService.SqlAgentResult result =
                service.run("question", SqlAccessContext.tenantUser("user-a", "tenant-a"));

        assertEquals("question", result.question());
        assertEquals("SELECT genre, avg_ms FROM genre_stats LIMIT 5", result.sql());
        assertEquals("命中 genre_stats 表，按租户过滤并保留 5 条样本。", result.debugSummary());
        assertEquals(rows, result.rows());
        assertSame(response, result.rawMsg());
        assertEquals(2, result.recordedToolUsages().size());
        assertTrue(result.memorySaved());
        verify(agent).call(any(Msg.class));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), argsCaptor.capture());
        assertEquals("SELECT genre, avg_ms FROM genre_stats WHERE genre_stats.tenant_id = ? LIMIT 5", sqlCaptor.getValue());
        assertEquals("tenant-a", argsCaptor.getValue()[0]);
        verify(memory).saveSuccessfulUsage(
                eq("question"),
                eq(SqlAccessContext.tenantUser("user-a", "tenant-a")),
                eq(List.of(
                        new RecordedToolUsage("sql_db_list_tables", Map.of("ignored", "")),
                        new RecordedToolUsage("sql_db_schema", Map.of("tableNames", "genre_stats")))),
                eq("SELECT genre, avg_ms FROM genre_stats LIMIT 5"));
    }

    @Test
    void runExecutesSqlLocallyFromSqlFence() {
        ReActAgent agent = mock(ReActAgent.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqlTools sqlTools = new SqlTools(jdbcTemplate, DatabaseDialect.POSTGRESQL);
        Msg response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .textContent("```sql\nSELECT * FROM users LIMIT 5\n```")
                        .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));
        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(jdbcTemplate.queryForList("SELECT * FROM users LIMIT 5")).thenReturn(rows);

        SqlAgentService service = new SqlAgentService(agent, sqlTools, mock(ToolUsageMemory.class), new SqlToolUsageRecorder());

        SqlAgentService.SqlAgentResult result =
                service.run("question", SqlAccessContext.admin("admin-user"));

        assertEquals("SELECT * FROM users LIMIT 5", result.sql());
        assertEquals("", result.debugSummary());
        assertEquals(rows, result.rows());
        verify(jdbcTemplate).queryForList("SELECT * FROM users LIMIT 5");
    }

    @Test
    void runAddsTenantGuidanceToPromptForNormalUsers() {
        ReActAgent agent = mock(ReActAgent.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqlTools sqlTools = new SqlTools(jdbcTemplate, DatabaseDialect.POSTGRESQL);
        Msg response = Msg.builder().role(MsgRole.ASSISTANT).textContent("SELECT order_count FROM orders LIMIT 1").build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        ToolUsageMemory memory = mock(ToolUsageMemory.class);
        when(memory.searchSimilarUsage(eq("查询今天订单数"), eq(SqlAccessContext.tenantUser("user-a", "tenant-a")), eq(3), eq(0.2d)))
                .thenReturn(List.of(
                        new ToolUsageMemorySearchResult(
                                "历史订单数问题",
                                "sql_db_schema",
                                Map.of("tableNames", "orders"),
                                0.85d,
                                OffsetDateTime.now())));

        SqlAgentService service = new SqlAgentService(agent, sqlTools, memory, new SqlToolUsageRecorder());

        service.run("查询今天订单数", SqlAccessContext.tenantUser("user-a", "tenant-a"));

        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        verify(agent, atLeastOnce()).call(captor.capture());
        Msg prompt = captor.getValue();
        assertTrue(prompt.getTextContent().contains("tenant-a"));
        assertTrue(prompt.getTextContent().contains("tenant_id"));
        assertTrue(prompt.getTextContent().contains("查询今天订单数"));
        assertTrue(prompt.getTextContent().contains("历史成功工具调用模式"));
        assertTrue(prompt.getTextContent().contains("sql_db_schema"));
        assertTrue(prompt.getTextContent().contains("tableNames=orders"));
        assertTrue(!prompt.getTextContent().contains("最新 schema 上下文"));
    }

    @Test
    void runDoesNotExecuteSqlWhenAgentRejectsOutOfScopeQuestion() {
        ReActAgent agent = mock(ReActAgent.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqlTools sqlTools = new SqlTools(jdbcTemplate, DatabaseDialect.POSTGRESQL);
        Msg response = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent(SqlAgentService.OUT_OF_SCOPE_REPLY)
                .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));
        ToolUsageMemory memory = mock(ToolUsageMemory.class);

        SqlAgentService service = new SqlAgentService(agent, sqlTools, memory, new SqlToolUsageRecorder());

        SqlAgentService.SqlAgentResult result = service.run("你会写 Java 吗", SqlAccessContext.admin("admin-user"));

        assertEquals("", result.sql());
        assertEquals("", result.debugSummary());
        assertTrue(result.rows().isEmpty());
        assertEquals(SqlAgentService.OUT_OF_SCOPE_REPLY, result.message());
        assertSame(response, result.rawMsg());
        assertEquals(0, result.memoryHitCount());
        assertEquals(false, result.memorySaved());
        verify(jdbcTemplate, never()).queryForList(anyString());
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
        verify(memory, never()).saveSuccessfulUsage(any(), any(), any(), any());
    }

    @Test
    void runKeepsHistoryAsHintInsteadOfPrefetchingSchema() {
        ReActAgent agent = mock(ReActAgent.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqlTools sqlTools = new SqlTools(jdbcTemplate, DatabaseDialect.POSTGRESQL);
        ToolUsageMemory memory = mock(ToolUsageMemory.class);
        when(memory.searchSimilarUsage(eq("查询未使用优惠券"), eq(SqlAccessContext.tenantUser("user-a", "tenant-a")), eq(3), eq(0.2d)))
                .thenReturn(List.of(
                        new ToolUsageMemorySearchResult(
                                "历史问题",
                                "sql_db_schema",
                                Map.of("tableNames", "users,coupons,user_coupons,orders"),
                                0.55d,
                                OffsetDateTime.now())));
        Msg response = Msg.builder().role(MsgRole.ASSISTANT).textContent("SELECT id FROM users LIMIT 1").build();
        when(agent.call(any(Msg.class))).thenAnswer(invocation -> {
            Msg prompt = invocation.getArgument(0);
            assertTrue(prompt.getTextContent().contains("历史成功工具调用模式"));
            assertTrue(prompt.getTextContent().contains("tableNames=users,coupons,user_coupons,orders"));
            return Mono.just(response);
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 1)));

        SqlToolUsageRecorder recorder = new SqlToolUsageRecorder();
        SqlAgentService service = new SqlAgentService(agent, sqlTools, memory, recorder);

        SqlAgentService.SqlAgentResult result =
                service.run("查询未使用优惠券", SqlAccessContext.tenantUser("user-a", "tenant-a"));

        assertEquals("SELECT id FROM users LIMIT 1", result.sql());
        assertEquals("", result.debugSummary());
        verify(agent).call(any(Msg.class));
        verify(memory).saveSuccessfulUsage(eq("查询未使用优惠券"), eq(SqlAccessContext.tenantUser("user-a", "tenant-a")), any(), eq("SELECT id FROM users LIMIT 1"));
    }

    @Test
    void extractDebugSummaryReadsJsonField() {
        String rawText = """
                {"sql":"SELECT id FROM users LIMIT 5","debug_summary":"围绕 users 表查询，并按租户限制最近注册用户。"}
                """;

        assertEquals("围绕 users 表查询，并按租户限制最近注册用户。", SqlAgentService.extractDebugSummary(rawText));
    }
}
