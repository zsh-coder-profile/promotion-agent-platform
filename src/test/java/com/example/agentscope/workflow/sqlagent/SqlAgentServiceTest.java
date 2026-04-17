package com.example.agentscope.workflow.sqlagent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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
        when(sqlTools.executeQuery("SELECT genre, avg_ms FROM genre_stats LIMIT 5")).thenReturn(rows);

        SqlAgentService service = new SqlAgentService(agent, sqlTools);

        SqlAgentService.SqlAgentResult result = service.run("question");

        assertEquals("question", result.question());
        assertEquals("SELECT genre, avg_ms FROM genre_stats LIMIT 5", result.sql());
        assertEquals(rows, result.rows());
        assertSame(response, result.rawMsg());
        verify(sqlTools).executeQuery("SELECT genre, avg_ms FROM genre_stats LIMIT 5");
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
        when(sqlTools.executeQuery("SELECT * FROM users LIMIT 5")).thenReturn(rows);

        SqlAgentService service = new SqlAgentService(agent, sqlTools);

        SqlAgentService.SqlAgentResult result = service.run("question");

        assertEquals("SELECT * FROM users LIMIT 5", result.sql());
        assertEquals(rows, result.rows());
    }
}
