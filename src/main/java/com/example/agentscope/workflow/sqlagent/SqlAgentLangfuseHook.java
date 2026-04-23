package com.example.agentscope.workflow.sqlagent;

import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adds human-readable SQL agent debug events onto the current OpenTelemetry span so Langfuse can
 * show the full planning/tool chain during troubleshooting.
 */
public class SqlAgentLangfuseHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(SqlAgentLangfuseHook.class);
    private static final int MAX_EVENT_TEXT_LENGTH = 8000;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent e) {
            addEvent("sqlagent.pre_reasoning", Map.of(
                    "agent", safe(e.getAgent().getName()),
                    "model", safe(e.getModelName()),
                    "input_messages", summarizeMessages(e.getInputMessages())));
        } else if (event instanceof PostReasoningEvent e) {
            addEvent("sqlagent.post_reasoning", Map.of(
                    "agent", safe(e.getAgent().getName()),
                    "reasoning_message", summarizeMessage(e.getReasoningMessage())));
        } else if (event instanceof PreActingEvent e) {
            addEvent("sqlagent.pre_acting", Map.of(
                    "agent", safe(e.getAgent().getName()),
                    "tool_name", safe(e.getToolUse().getName()),
                    "tool_input", safe(String.valueOf(e.getToolUse().getInput()))));
        } else if (event instanceof PostActingEvent e) {
            addEvent("sqlagent.post_acting", Map.of(
                    "agent", safe(e.getAgent().getName()),
                    "tool_name", safe(e.getToolUse().getName()),
                    "tool_input", safe(String.valueOf(e.getToolUse().getInput())),
                    "tool_output", summarizeToolResult(e.getToolResult())));
        } else if (event instanceof PostCallEvent e) {
            addEvent("sqlagent.final_message", Map.of(
                    "agent", safe(e.getAgent().getName()),
                    "final_message", summarizeMessage(e.getFinalMessage())));
        } else if (event instanceof ErrorEvent e) {
            addEvent("sqlagent.error", Map.of(
                    "agent", safe(e.getAgent().getName()),
                    "error_type", safe(e.getError().getClass().getName()),
                    "error_message", safe(e.getError().getMessage())));
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 50;
    }

    static String summarizeMessages(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(SqlAgentLangfuseHook::summarizeMessage)
                .collect(Collectors.joining("\n---\n"));
    }

    static String summarizeMessage(Msg msg) {
        if (msg == null) {
            return "";
        }
        String content = msg.getContent() == null ? safe(msg.getTextContent()) : msg.getContent().stream()
                .map(SqlAgentLangfuseHook::summarizeContentBlock)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
        if (content.isBlank()) {
            content = safe(msg.getTextContent());
        }
        return ("role=%s name=%s\n%s".formatted(msg.getRole(), safe(msg.getName()), content)).trim();
    }

    static String summarizeToolResult(ToolResultBlock toolResult) {
        if (toolResult == null) {
            return "";
        }
        String output = toolResult.getOutput() == null ? "" : toolResult.getOutput().stream()
                .map(SqlAgentLangfuseHook::summarizeContentBlock)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
        if (output.isBlank()) {
            output = safe(String.valueOf(toolResult.getMetadata()));
        }
        return safe(output);
    }

    private static String summarizeContentBlock(ContentBlock block) {
        if (block instanceof TextBlock textBlock) {
            return textBlock.getText();
        }
        if (block instanceof ToolUseBlock toolUseBlock) {
            return "tool_use name=%s input=%s".formatted(
                    safe(toolUseBlock.getName()),
                    safe(String.valueOf(toolUseBlock.getInput())));
        }
        if (block instanceof ToolResultBlock toolResultBlock) {
            return "tool_result name=%s output=%s".formatted(
                    safe(toolResultBlock.getName()),
                    summarizeToolResult(toolResultBlock));
        }
        return safe(block == null ? "" : block.toString());
    }

    private void addEvent(String eventName, Map<String, String> values) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) {
            return;
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> sanitized.put(key, safe(value)));
        AttributesBuilder builder = new AttributesBuilder();
        sanitized.forEach(builder::put);
        span.addEvent(eventName, builder.build());
        log.info("{} {}", eventName, sanitized);
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_EVENT_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_EVENT_TEXT_LENGTH) + "...(truncated)";
    }

    private static final class AttributesBuilder {
        private final Map<AttributeKey<String>, String> values = new LinkedHashMap<>();

        void put(String key, String value) {
            values.put(AttributeKey.stringKey(key), value);
        }

        Attributes build() {
            AttributesBuilderImpl impl = new AttributesBuilderImpl();
            values.forEach(impl::put);
            return impl.build();
        }
    }

    private static final class AttributesBuilderImpl {
        private final io.opentelemetry.api.common.AttributesBuilder delegate = Attributes.builder();

        void put(AttributeKey<String> key, String value) {
            delegate.put(key, value);
        }

        Attributes build() {
            return delegate.build();
        }
    }
}
