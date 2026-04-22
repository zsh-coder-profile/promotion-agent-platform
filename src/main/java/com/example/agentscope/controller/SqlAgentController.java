package com.example.agentscope.controller;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import com.example.agentscope.workflow.sqlagent.SqlAgentService;
import com.example.agentscope.workflow.sqlagent.SqlAgentService.SqlAgentResult;
import com.example.agentscope.workflow.sqlagent.chat.SqlChatRepository;
import com.example.agentscope.workflow.sqlagent.chat.SqlChatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql")
@ConditionalOnProperty(name = "workflow.sql.enabled", havingValue = "true")
public class SqlAgentController {

    private final SqlAgentService sqlAgentService;
    private final SqlChatService sqlChatService;

    public SqlAgentController(SqlAgentService sqlAgentService, SqlChatService sqlChatService) {
        this.sqlAgentService = sqlAgentService;
        this.sqlChatService = sqlChatService;
    }

    /**
     * POST /api/sql/query
     * Body: { "question": "哪个流派的平均曲目时长最长？" }
     */
    @PostMapping("/query")
    public Map<String, Object> query(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        String question = body.getOrDefault("question", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);

        try {
            SqlAccessContext accessContext = SqlAccessContext.fromHeaders(userId, tenantId, userRole);
            SqlAgentResult agentResult = sqlAgentService.run(question, accessContext);
            result.put("status", "success");
            result.put("sql", agentResult.sql());
            result.put("rows", agentResult.rows());
            result.put("rowCount", agentResult.rows().size());
            result.put("memoryHits", agentResult.memoryHitCount());
            result.put("memoryApplied", agentResult.memoryApplied());
            result.put("memorySaved", agentResult.memorySaved());
            if (agentResult.message() != null && !agentResult.message().isBlank()) {
                result.put("message", agentResult.message());
            }
            result.put("userId", accessContext.userId());
            result.put("role", accessContext.role());
            result.put("tenantId", accessContext.tenantId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/conversations")
    public List<SqlChatRepository.ConversationSummary> listConversations(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        SqlAccessContext accessContext = SqlAccessContext.fromHeaders(userId, tenantId, userRole);
        return sqlChatService.listConversations(accessContext);
    }

    @PostMapping("/conversations")
    public SqlChatService.ChatConversationDetail createConversation(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        SqlAccessContext accessContext = SqlAccessContext.fromHeaders(userId, tenantId, userRole);
        return sqlChatService.createConversation(accessContext);
    }

    @GetMapping("/conversations/{conversationId}")
    public SqlChatService.ChatConversationDetail getConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        SqlAccessContext accessContext = SqlAccessContext.fromHeaders(userId, tenantId, userRole);
        return sqlChatService.getConversation(conversationId, accessContext);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public SqlChatService.ChatConversationDetail sendMessage(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        SqlAccessContext accessContext = SqlAccessContext.fromHeaders(userId, tenantId, userRole);
        return sqlChatService.sendMessage(conversationId, body.get("question"), accessContext);
    }
}
