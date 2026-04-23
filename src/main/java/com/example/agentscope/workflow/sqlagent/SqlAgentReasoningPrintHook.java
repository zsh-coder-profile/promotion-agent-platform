package com.example.agentscope.workflow.sqlagent;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Prints ReAct reasoning content to stdout for local debugging.
 */
public class SqlAgentReasoningPrintHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(SqlAgentReasoningPrintHook.class);

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent e) {
            String printable = extractPrintableContent(e.getReasoningMessage());
            if (!printable.isBlank()) {
                log.info("[{}] reasoning: {}", e.getAgent().getName(), printable);
            }
        }
        return Mono.just(event);
    }

    static String extractPrintableContent(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock thinkingBlock) {
                appendLine(builder, thinkingBlock.getThinking());
            } else if (block instanceof TextBlock textBlock) {
                appendLine(builder, textBlock.getText());
            } else if (block instanceof ToolUseBlock toolUseBlock) {
                appendLine(
                        builder,
                        "tool_use name=%s input=%s".formatted(
                                toolUseBlock.getName(),
                                String.valueOf(toolUseBlock.getInput())));
            }
        }
        return builder.toString().trim();
    }

    private static void appendLine(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text);
    }
}
