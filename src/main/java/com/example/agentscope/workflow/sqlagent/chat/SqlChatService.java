package com.example.agentscope.workflow.sqlagent.chat;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.example.agentscope.workflow.sqlagent.SqlAgentService;
import com.example.agentscope.workflow.sqlagent.SqlAgentService.SqlAgentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SqlChatService {

    private static final int HISTORY_WINDOW_SIZE = 6;
    private static final int TITLE_MAX_LENGTH = 24;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ROWS_TYPE = new TypeReference<>() {};

    private final SqlAgentService sqlAgentService;
    private final SqlChatRepository sqlChatRepository;

    public SqlChatService(SqlAgentService sqlAgentService, SqlChatRepository sqlChatRepository) {
        this.sqlAgentService = sqlAgentService;
        this.sqlChatRepository = sqlChatRepository;
    }

    public List<SqlChatRepository.ConversationSummary> listConversations(SqlAccessContext accessContext) {
        return sqlChatRepository.listConversations(accessContext);
    }

    @Transactional
    public ChatConversationDetail createConversation(SqlAccessContext accessContext) {
        SqlChatRepository.ConversationSummary summary =
                sqlChatRepository.createConversation(accessContext, "新对话");
        return new ChatConversationDetail(summary, List.of());
    }

    public ChatConversationDetail getConversation(String conversationId, SqlAccessContext accessContext) {
        SqlChatRepository.ConversationSummary summary = sqlChatRepository.getConversation(conversationId, accessContext)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问。"));
        List<ChatMessageView> messages = sqlChatRepository.listMessages(conversationId).stream()
                .map(this::toMessageView)
                .toList();
        return new ChatConversationDetail(summary, messages);
    }

    @Transactional
    public ChatConversationDetail sendMessage(String conversationId, String question, SqlAccessContext accessContext) {
        String normalizedQuestion = question == null ? "" : question.trim();
        if (normalizedQuestion.isEmpty()) {
            throw new IllegalArgumentException("问题不能为空。");
        }

        SqlChatRepository.ConversationSummary summary = sqlChatRepository.getConversation(conversationId, accessContext)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问。"));

        sqlChatRepository.saveUserMessage(conversationId, normalizedQuestion);
        List<SqlChatRepository.ConversationMessageRecord> existingMessages = sqlChatRepository.listMessages(conversationId);
        List<SqlAgentService.ChatHistoryTurn> historyTurns = buildHistoryTurns(existingMessages);
        String conversationTitle = buildTitle(summary.title(), normalizedQuestion);

        try {
            SqlAgentResult agentResult = sqlAgentService.run(normalizedQuestion, accessContext, historyTurns);
            sqlChatRepository.saveAssistantMessage(
                    conversationId,
                    buildAssistantAnswer(agentResult, accessContext),
                    agentResult.sql(),
                    agentResult.debugSummary(),
                    toJson(agentResult.rows()),
                    agentResult.rows().size(),
                    "SUCCESS",
                    null);
            sqlChatRepository.touchConversation(conversationId, conversationTitle, normalizedQuestion);
        } catch (RuntimeException e) {
            sqlChatRepository.saveAssistantMessage(
                    conversationId,
                    "查询失败，请调整问题后重试。",
                    null,
                    null,
                    null,
                    0,
                    "ERROR",
                    e.getMessage());
            sqlChatRepository.touchConversation(conversationId, conversationTitle, normalizedQuestion);
        }

        return getConversation(conversationId, accessContext);
    }

    private List<SqlAgentService.ChatHistoryTurn> buildHistoryTurns(
            List<SqlChatRepository.ConversationMessageRecord> existingMessages) {
        List<SqlAgentService.ChatHistoryTurn> turns = existingMessages.stream()
                .filter(message -> "ASSISTANT".equals(message.role()))
                .map(message -> {
                    String question = findQuestionBefore(existingMessages, message.id());
                    return new SqlAgentService.ChatHistoryTurn(
                            question,
                            message.answerText(),
                            message.sqlText());
                })
                .filter(turn -> turn.question() != null && !turn.question().isBlank())
                .toList();
        int fromIndex = Math.max(0, turns.size() - HISTORY_WINDOW_SIZE);
        return turns.subList(fromIndex, turns.size());
    }

    private String findQuestionBefore(
            List<SqlChatRepository.ConversationMessageRecord> messages,
            String assistantMessageId) {
        String latestQuestion = null;
        for (SqlChatRepository.ConversationMessageRecord message : messages) {
            if (message.id().equals(assistantMessageId)) {
                return latestQuestion;
            }
            if ("USER".equals(message.role()) && message.question() != null && !message.question().isBlank()) {
                latestQuestion = message.question();
            }
        }
        return latestQuestion;
    }

    private ChatMessageView toMessageView(SqlChatRepository.ConversationMessageRecord message) {
        List<Map<String, Object>> rows = parseRows(message.resultRowsJson());
        return new ChatMessageView(
                message.id(),
                message.role(),
                message.question(),
                message.answerText(),
                message.sqlText(),
                message.debugSummary(),
                rows,
                message.resultRowCount() == null ? 0 : message.resultRowCount(),
                message.status(),
                message.errorMessage(),
                message.createdAt());
    }

    private List<Map<String, Object>> parseRows(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, ROWS_TYPE);
        } catch (JsonProcessingException e) {
            return List.of(Map.of("raw", json));
        }
    }

    private String toJson(List<Map<String, Object>> rows) {
        try {
            return JSON.writeValueAsString(rows == null ? List.of() : rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize query rows.", e);
        }
    }

    private String buildTitle(String currentTitle, String question) {
        if (currentTitle != null && !"新对话".equals(currentTitle)) {
            return currentTitle;
        }
        if (question.length() <= TITLE_MAX_LENGTH) {
            return question;
        }
        return question.substring(0, TITLE_MAX_LENGTH) + "...";
    }

    private String buildAssistantAnswer(SqlAgentResult agentResult, SqlAccessContext accessContext) {
        if (agentResult.message() != null && !agentResult.message().isBlank()) {
            return agentResult.message();
        }
        return "%s查询完成，返回 %d 条结果。".formatted(
                accessContext.isAdmin() ? "管理员视角" : "租户 " + accessContext.tenantId(),
                agentResult.rows().size());
    }

    public record ChatConversationDetail(
            SqlChatRepository.ConversationSummary conversation,
            List<ChatMessageView> messages) {
    }

    public record ChatMessageView(
            String id,
            String role,
            String question,
            String answerText,
            String sqlText,
            String debugSummary,
            List<Map<String, Object>> rows,
            int rowCount,
            String status,
            String errorMessage,
            java.time.LocalDateTime createdAt) {
    }
}
