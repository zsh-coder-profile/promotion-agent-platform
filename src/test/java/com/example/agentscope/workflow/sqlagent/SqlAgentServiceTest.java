package com.example.agentscope.workflow.sqlagent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlAgentServiceTest {

    @Test
    void runExecutesSqlLocallyFromJsonResponse() {
        ReActAgent agent = mock(ReActAgent.class);
        SqlTools sqlTools = mock(SqlTools.class);
        Msg response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .textContent("{\"sql\":\"SELECT genre, avg_ms FROM genre_stats LIMIT 5\"}")
                        .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));
        List<Map<String, Object>> rows = List.of(Map.of("genre", "Jazz", "avg_ms", 123));
        when(sqlTools.executeQuery(
                "SELECT genre, avg_ms FROM genre_stats LIMIT 5",
                SqlAccessContext.tenantUser("user-a", "tenant-a")))
                .thenReturn(rows);

        SqlAgentService service = new SqlAgentService(agent, sqlTools);

        SqlAgentService.SqlAgentResult result =
                service.run("question", SqlAccessContext.tenantUser("user-a", "tenant-a"));

        assertEquals("question", result.question());
        assertEquals("SELECT genre, avg_ms FROM genre_stats LIMIT 5", result.sql());
        assertEquals(rows, result.rows());
        assertSame(response, result.rawMsg());
        verify(agent).call(any(Msg.class));
        verify(sqlTools).executeQuery(
                "SELECT genre, avg_ms FROM genre_stats LIMIT 5",
                SqlAccessContext.tenantUser("user-a", "tenant-a"));
    }

    @Test
    void runExecutesSqlLocallyFromSqlFence() {
        ReActAgent agent = mock(ReActAgent.class);
        SqlTools sqlTools = mock(SqlTools.class);
        Msg response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .textContent("```sql\nSELECT * FROM users LIMIT 5\n```")
                        .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));
        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(sqlTools.executeQuery("SELECT * FROM users LIMIT 5", SqlAccessContext.admin("admin-user")))
                .thenReturn(rows);

        SqlAgentService service = new SqlAgentService(agent, sqlTools);

        SqlAgentService.SqlAgentResult result =
                service.run("question", SqlAccessContext.admin("admin-user"));

        assertEquals("SELECT * FROM users LIMIT 5", result.sql());
        assertEquals(rows, result.rows());
        verify(sqlTools).executeQuery("SELECT * FROM users LIMIT 5", SqlAccessContext.admin("admin-user"));
    }

    @Test
    void runAddsTenantGuidanceToPromptForNormalUsers() {
        ReActAgent agent = mock(ReActAgent.class);
        SqlTools sqlTools = mock(SqlTools.class);
        Msg response = Msg.builder().role(MsgRole.ASSISTANT).textContent("SELECT 1").build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));
        when(sqlTools.executeQuery(eq("SELECT 1"), eq(SqlAccessContext.tenantUser("user-a", "tenant-a"))))
                .thenReturn(List.of());

        SqlAgentService service = new SqlAgentService(agent, sqlTools);

        service.run("查询今天订单数", SqlAccessContext.tenantUser("user-a", "tenant-a"));

        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        verify(agent, atLeastOnce()).call(captor.capture());
        Msg prompt = captor.getValue();
        assertTrue(prompt.getTextContent().contains("tenant-a"));
        assertTrue(prompt.getTextContent().contains("tenant_id"));
        assertTrue(prompt.getTextContent().contains("查询今天订单数"));
    }
}
