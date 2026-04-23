package com.example.agentscope.workflow.sqlagent.chat;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SqlChatRepository {

    private static final RowMapper<ConversationSummary> CONVERSATION_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new ConversationSummary(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("last_question"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at").toLocalDateTime());

    private static final RowMapper<ConversationMessageRecord> MESSAGE_ROW_MAPPER = (rs, rowNum) ->
            new ConversationMessageRecord(
                    rs.getString("id"),
                    rs.getString("conversation_id"),
                    rs.getString("message_role"),
                    rs.getString("question"),
                    rs.getString("answer_text"),
                    rs.getString("sql_text"),
                    rs.getString("debug_summary"),
                    rs.getString("result_rows_json"),
                    rs.getObject("result_row_count", Integer.class),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public SqlChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConversationSummary createConversation(SqlAccessContext accessContext, String title) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                        INSERT INTO sql_chat_conversation (
                            id, owner_user_id, owner_tenant_id, owner_role, title, created_at, updated_at, last_question
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                accessContext.userId(),
                accessContext.tenantId(),
                accessContext.role(),
                title,
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                null);
        return getConversation(id, accessContext)
                .orElseThrow(() -> new IllegalStateException("Conversation was created but cannot be loaded."));
    }

    public List<ConversationSummary> listConversations(SqlAccessContext accessContext) {
        if (accessContext.isAdmin()) {
            return jdbcTemplate.query("""
                            SELECT id, title, last_question, created_at, updated_at
                            FROM sql_chat_conversation
                            WHERE owner_user_id = ? AND owner_role = ?
                            ORDER BY updated_at DESC, created_at DESC
                            """,
                    CONVERSATION_SUMMARY_ROW_MAPPER,
                    accessContext.userId(),
                    accessContext.role());
        }
        return jdbcTemplate.query("""
                        SELECT id, title, last_question, created_at, updated_at
                        FROM sql_chat_conversation
                        WHERE owner_user_id = ? AND owner_role = ? AND owner_tenant_id = ?
                        ORDER BY updated_at DESC, created_at DESC
                        """,
                CONVERSATION_SUMMARY_ROW_MAPPER,
                accessContext.userId(),
                accessContext.role(),
                accessContext.tenantId());
    }

    public Optional<ConversationSummary> getConversation(String conversationId, SqlAccessContext accessContext) {
        List<ConversationSummary> summaries;
        if (accessContext.isAdmin()) {
            summaries = jdbcTemplate.query("""
                            SELECT id, title, last_question, created_at, updated_at
                            FROM sql_chat_conversation
                            WHERE id = ? AND owner_user_id = ? AND owner_role = ?
                            """,
                    CONVERSATION_SUMMARY_ROW_MAPPER,
                    conversationId,
                    accessContext.userId(),
                    accessContext.role());
        } else {
            summaries = jdbcTemplate.query("""
                            SELECT id, title, last_question, created_at, updated_at
                            FROM sql_chat_conversation
                            WHERE id = ? AND owner_user_id = ? AND owner_role = ? AND owner_tenant_id = ?
                            """,
                    CONVERSATION_SUMMARY_ROW_MAPPER,
                    conversationId,
                    accessContext.userId(),
                    accessContext.role(),
                    accessContext.tenantId());
        }
        return summaries.stream().findFirst();
    }

    public List<ConversationMessageRecord> listMessages(String conversationId) {
        return jdbcTemplate.query("""
                        SELECT id, conversation_id, message_role, question, answer_text, sql_text, debug_summary,
                               result_rows_json, result_row_count, status, error_message, created_at
                        FROM sql_chat_message
                        WHERE conversation_id = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                MESSAGE_ROW_MAPPER,
                conversationId);
    }

    public String saveUserMessage(String conversationId, String question) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                        INSERT INTO sql_chat_message (
                            id, conversation_id, message_role, question, status, created_at
                        ) VALUES (?, ?, 'USER', ?, 'SUCCESS', ?)
                        """,
                id,
                conversationId,
                question,
                Timestamp.valueOf(LocalDateTime.now()));
        return id;
    }

    public String saveAssistantMessage(
            String conversationId,
            String answerText,
            String sqlText,
            String debugSummary,
            String resultRowsJson,
            Integer resultRowCount,
            String status,
            String errorMessage) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                        INSERT INTO sql_chat_message (
                            id, conversation_id, message_role, answer_text, sql_text, debug_summary, result_rows_json,
                            result_row_count, status, error_message, created_at
                        ) VALUES (?, ?, 'ASSISTANT', ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                conversationId,
                answerText,
                sqlText,
                debugSummary,
                resultRowsJson,
                resultRowCount,
                status,
                errorMessage,
                Timestamp.valueOf(LocalDateTime.now()));
        return id;
    }

    public void touchConversation(String conversationId, String title, String lastQuestion) {
        jdbcTemplate.update("""
                        UPDATE sql_chat_conversation
                        SET title = ?, last_question = ?, updated_at = ?
                        WHERE id = ?
                        """,
                title,
                lastQuestion,
                Timestamp.valueOf(LocalDateTime.now()),
                conversationId);
    }

    public record ConversationSummary(
            String id,
            String title,
            String lastQuestion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ConversationMessageRecord(
            String id,
            String conversationId,
            String role,
            String question,
            String answerText,
            String sqlText,
            String debugSummary,
            String resultRowsJson,
            Integer resultRowCount,
            String status,
            String errorMessage,
            LocalDateTime createdAt) {
    }
}
