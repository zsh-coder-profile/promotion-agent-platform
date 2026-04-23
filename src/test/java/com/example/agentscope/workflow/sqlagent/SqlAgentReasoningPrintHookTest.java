package com.example.agentscope.workflow.sqlagent;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAgentReasoningPrintHookTest {

    @Test
    void extractPrintableContentIncludesThinkingAndTextOnly() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(List.of(
                        ThinkingBlock.builder().thinking("先分析用户问题。").build(),
                        TextBlock.builder().text("接着整理最终回复。").build(),
                        ToolUseBlock.builder()
                                .id("tool-1")
                                .name("sql_db_schema")
                                .input(Map.of("tableNames", "users"))
                                .build()))
                .build();

        String printable = SqlAgentReasoningPrintHook.extractPrintableContent(msg);

        assertEquals("""
                先分析用户问题。
                接着整理最终回复。
                tool_use name=sql_db_schema input={tableNames=users}
                """.trim(), printable);
    }

    @Test
    void extractPrintableContentReturnsEmptyForNullMessage() {
        assertEquals("", SqlAgentReasoningPrintHook.extractPrintableContent(null));
    }

    @Test
    void generateQueryPromptIncludesDebugInstructionsWhenVerboseReasoningEnabled() {
        String prompt = SqlAgentConfig.generateQueryPrompt("PostgreSQL", true);

        assertTrue(prompt.contains("调试模式说明"));
        assertTrue(prompt.contains("在每一轮调用工具之前"));
        assertTrue(prompt.contains("最终答案仍然必须遵守下面的 JSON 输出要求"));
    }
}
