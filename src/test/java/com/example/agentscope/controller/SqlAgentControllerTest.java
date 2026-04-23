package com.example.agentscope.controller;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.example.agentscope.workflow.sqlagent.SqlAgentService;
import com.example.agentscope.workflow.sqlagent.SqlAgentService.SqlAgentResult;
import com.example.agentscope.workflow.sqlagent.chat.SqlChatService;
import com.example.agentscope.workflow.sqlagent.memory.ToolUsageMemorySearchResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlAgentControllerTest {

    @Test
    void queryBuildsTenantUserAccessContextFromHeaders() {
        SqlAgentService service = mock(SqlAgentService.class);
        SqlChatService chatService = mock(SqlChatService.class);
        SqlAgentController controller = new SqlAgentController(service, chatService);
        SqlAgentResult result = new SqlAgentResult(
                "本月订单数",
                "SELECT count(*) AS order_count FROM orders WHERE tenant_id = 'tenant-a'",
                "围绕 orders 表统计本月订单数，并按租户过滤。",
                List.of(Map.of("order_count", 12)),
                Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build(),
                "",
                List.of(new ToolUsageMemorySearchResult("历史问题", "sql_db_schema", Map.of("tableNames", "orders"), 0.9d, null)),
                true,
                true,
                List.of());
        when(service.run(eq("本月订单数"), eq(SqlAccessContext.tenantUser("user-a", "tenant-a"))))
                .thenReturn(result);

        Map<String, Object> response = controller.query(
                Map.of("question", "本月订单数"),
                "user-a",
                "tenant-a",
                "USER");

        assertEquals("success", response.get("status"));
        assertEquals(1, response.get("rowCount"));
        assertEquals(1, response.get("memoryHits"));
        assertEquals(true, response.get("memoryApplied"));
        assertEquals(true, response.get("memorySaved"));
        assertEquals("围绕 orders 表统计本月订单数，并按租户过滤。", response.get("debugSummary"));
        verify(service).run("本月订单数", SqlAccessContext.tenantUser("user-a", "tenant-a"));
    }

    @Test
    void queryBuildsAdminAccessContextFromHeaders() {
        SqlAgentService service = mock(SqlAgentService.class);
        SqlChatService chatService = mock(SqlChatService.class);
        SqlAgentController controller = new SqlAgentController(service, chatService);
        SqlAgentResult result = new SqlAgentResult(
                "全局订单数",
                "SELECT count(*) AS order_count FROM orders",
                "围绕 orders 表做全局统计。",
                List.of(Map.of("order_count", 20)),
                Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build(),
                "",
                List.of(),
                false,
                false,
                List.of());
        when(service.run(eq("全局订单数"), eq(SqlAccessContext.admin("admin-user"))))
                .thenReturn(result);

        Map<String, Object> response = controller.query(
                Map.of("question", "全局订单数"),
                "admin-user",
                "tenant-a",
                "ADMIN");

        assertEquals("success", response.get("status"));
        assertEquals(1, response.get("rowCount"));
        assertEquals(0, response.get("memoryHits"));
        assertEquals("围绕 orders 表做全局统计。", response.get("debugSummary"));
        verify(service).run("全局订单数", SqlAccessContext.admin("admin-user"));
    }

    @Test
    void queryReturnsFriendlyMessageForOutOfScopeQuestion() {
        SqlAgentService service = mock(SqlAgentService.class);
        SqlChatService chatService = mock(SqlChatService.class);
        SqlAgentController controller = new SqlAgentController(service, chatService);
        SqlAgentResult result = new SqlAgentResult(
                "你会写 Java 吗",
                "",
                "",
                List.of(),
                Msg.builder().role(MsgRole.ASSISTANT).textContent(SqlAgentService.OUT_OF_SCOPE_REPLY).build(),
                SqlAgentService.OUT_OF_SCOPE_REPLY,
                List.of(),
                false,
                false,
                List.of());
        when(service.run(eq("你会写 Java 吗"), eq(SqlAccessContext.admin("admin-user"))))
                .thenReturn(result);

        Map<String, Object> response = controller.query(
                Map.of("question", "你会写 Java 吗"),
                "admin-user",
                "tenant-a",
                "ADMIN");

        assertEquals("success", response.get("status"));
        assertEquals("", response.get("sql"));
        assertEquals(0, response.get("rowCount"));
        assertEquals(SqlAgentService.OUT_OF_SCOPE_REPLY, response.get("message"));
    }
}
