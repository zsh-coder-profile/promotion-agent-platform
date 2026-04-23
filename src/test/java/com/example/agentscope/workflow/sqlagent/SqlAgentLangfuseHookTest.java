package com.example.agentscope.workflow.sqlagent;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAgentLangfuseHookTest {

    @Test
    void summarizeMessageIncludesTextAndToolCalls() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(List.of(
                        TextBlock.builder().text("先看看有哪些表").build(),
                        ToolUseBlock.builder()
                                .id("tool-1")
                                .name("sql_db_list_tables")
                                .input(Map.of("limit", 5))
                                .build()))
                .build();

        String summary = SqlAgentLangfuseHook.summarizeMessage(msg);

        assertTrue(summary.contains("ASSISTANT"));
        assertTrue(summary.contains("先看看有哪些表"));
        assertTrue(summary.contains("sql_db_list_tables"));
        assertTrue(summary.contains("limit=5"));
    }

    @Test
    void summarizeToolResultIncludesOutputText() {
        ToolResultBlock result = ToolResultBlock.of(
                "tool-1",
                "sql_db_schema",
                TextBlock.builder().text("users(id, tenant_id)").build());

        String summary = SqlAgentLangfuseHook.summarizeToolResult(result);

        assertTrue(summary.contains("users(id, tenant_id)"));
    }
}
