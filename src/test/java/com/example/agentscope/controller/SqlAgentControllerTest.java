package com.example.agentscope.controller;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.example.agentscope.workflow.sqlagent.SqlAgentService;
import com.example.agentscope.workflow.sqlagent.SqlAgentService.SqlAgentResult;
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
        SqlAgentController controller = new SqlAgentController(service);
        SqlAgentResult result = new SqlAgentResult(
                "本月订单数",
                "SELECT count(*) AS order_count FROM orders WHERE tenant_id = 'tenant-a'",
                List.of(Map.of("order_count", 12)),
                Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build());
        when(service.run(eq("本月订单数"), eq(SqlAccessContext.tenantUser("user-a", "tenant-a"))))
                .thenReturn(result);

        Map<String, Object> response = controller.query(
                Map.of("question", "本月订单数"),
                "user-a",
                "tenant-a",
                "USER");

        assertEquals("success", response.get("status"));
        assertEquals(1, response.get("rowCount"));
        verify(service).run("本月订单数", SqlAccessContext.tenantUser("user-a", "tenant-a"));
    }

    @Test
    void queryBuildsAdminAccessContextFromHeaders() {
        SqlAgentService service = mock(SqlAgentService.class);
        SqlAgentController controller = new SqlAgentController(service);
        SqlAgentResult result = new SqlAgentResult(
                "全局订单数",
                "SELECT count(*) AS order_count FROM orders",
                List.of(Map.of("order_count", 20)),
                Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build());
        when(service.run(eq("全局订单数"), eq(SqlAccessContext.admin("admin-user"))))
                .thenReturn(result);

        Map<String, Object> response = controller.query(
                Map.of("question", "全局订单数"),
                "admin-user",
                "tenant-a",
                "ADMIN");

        assertEquals("success", response.get("status"));
        assertEquals(1, response.get("rowCount"));
        verify(service).run("全局订单数", SqlAccessContext.admin("admin-user"));
    }
}
