package com.example.agentscope.workflow.sqlagent.chat;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.example.agentscope.workflow.sqlagent.SqlAgentService;
import com.example.agentscope.workflow.sqlagent.SqlAgentService.SqlAgentResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlChatServiceTest {

    @Test
    void sendMessagePersistsAndReturnsDebugSummary() {
        SqlAgentService sqlAgentService = mock(SqlAgentService.class);
        SqlChatRepository sqlChatRepository = mock(SqlChatRepository.class);
        SqlChatService chatService = new SqlChatService(sqlAgentService, sqlChatRepository);
        SqlAccessContext accessContext = SqlAccessContext.tenantUser("user-a", "tenant-a");
        SqlChatRepository.ConversationSummary summary = new SqlChatRepository.ConversationSummary(
                "conv-1",
                "新对话",
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
        SqlAgentResult agentResult = new SqlAgentResult(
                "有哪些用户",
                "SELECT id, username FROM users LIMIT 5",
                "围绕 users 表查询用户列表，并按租户过滤最近注册的 5 条。",
                List.of(Map.of("id", 1, "username", "alice")),
                Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build(),
                "",
                List.of(),
                false,
                false,
                List.of());
        SqlChatRepository.ConversationMessageRecord userMessage = new SqlChatRepository.ConversationMessageRecord(
                "msg-user-1",
                "conv-1",
                "USER",
                "有哪些用户",
                null,
                null,
                null,
                null,
                null,
                "SUCCESS",
                null,
                LocalDateTime.now());
        SqlChatRepository.ConversationMessageRecord assistantMessage = new SqlChatRepository.ConversationMessageRecord(
                "msg-assistant-1",
                "conv-1",
                "ASSISTANT",
                null,
                "租户 tenant-a 查询完成，返回 1 条结果。",
                "SELECT id, username FROM users LIMIT 5",
                "围绕 users 表查询用户列表，并按租户过滤最近注册的 5 条。",
                "[{\"id\":1,\"username\":\"alice\"}]",
                1,
                "SUCCESS",
                null,
                LocalDateTime.now());

        when(sqlChatRepository.getConversation("conv-1", accessContext)).thenReturn(Optional.of(summary));
        when(sqlChatRepository.listMessages("conv-1"))
                .thenReturn(List.of())
                .thenReturn(List.of(userMessage, assistantMessage));
        when(sqlAgentService.run(eq("有哪些用户"), eq(accessContext), any())).thenReturn(agentResult);

        SqlChatService.ChatConversationDetail detail = chatService.sendMessage("conv-1", "有哪些用户", accessContext);

        verify(sqlChatRepository).saveAssistantMessage(
                eq("conv-1"),
                eq("租户 tenant-a查询完成，返回 1 条结果。"),
                eq("SELECT id, username FROM users LIMIT 5"),
                eq("围绕 users 表查询用户列表，并按租户过滤最近注册的 5 条。"),
                any(),
                eq(1),
                eq("SUCCESS"),
                eq(null));
        assertEquals(2, detail.messages().size());
        assertEquals("围绕 users 表查询用户列表，并按租户过滤最近注册的 5 条。", detail.messages().get(1).debugSummary());
        assertTrue(detail.messages().get(1).rows().contains(Map.of("id", 1, "username", "alice")));
    }
}
