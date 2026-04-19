package com.example.agentscope.workflow.sqlagent;

import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that asks the LLM to plan SQL from schema metadata, then executes the SQL locally.
 */
public class SqlAgentService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SQL_BLOCK =
            Pattern.compile("```sql\\s*(.*?)\\s*```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ReActAgent sqlAgent;
    private final SqlTools sqlTools;

    public SqlAgentService(ReActAgent sqlAgent, SqlTools sqlTools) {
        this.sqlAgent = sqlAgent;
        this.sqlTools = sqlTools;
    }

    /**
     * Plan SQL with the model and execute it locally without sending query results back to the LLM.
     */
    public SqlAgentResult run(String question, SqlAccessContext accessContext) {
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(buildQuestionWithAccessContext(question, accessContext))
                .build();
        Msg responseMsg = sqlAgent.call(userMsg).block();
        String rawText = responseMsg != null ? responseMsg.getTextContent() : "";
        String sql = extractSql(rawText);
        List<Map<String, Object>> rows = sqlTools.executeQuery(sql, accessContext);

        return new SqlAgentResult(question, sql, rows, responseMsg);
    }

    static String buildQuestionWithAccessContext(String question, SqlAccessContext accessContext) {
        if (accessContext == null) {
            return question;
        }
        if (accessContext.isAdmin()) {
            return question + "\n\n[系统上下文]\n当前登录用户是平台管理员，可以查看全量数据。";
        }
        return question
                + "\n\n[系统上下文]\n当前登录用户: "
                + accessContext.userId()
                + "\n当前租户: "
                + accessContext.tenantId()
                + "\n生成 SQL 时，所有业务表都必须使用 tenant_id = '"
                + accessContext.tenantId()
                + "' 做过滤。";
    }

    static String extractSql(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalStateException("LLM did not return SQL.");
        }
        String jsonSql = tryExtractJsonSql(rawText);
        if (jsonSql != null) {
            return jsonSql;
        }
        Matcher matcher = SQL_BLOCK.matcher(rawText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return rawText.trim();
    }

    private static String tryExtractJsonSql(String rawText) {
        try {
            JsonNode node = JSON.readTree(rawText);
            JsonNode sqlNode = node.get("sql");
            if (sqlNode != null && !sqlNode.asText().isBlank()) {
                return sqlNode.asText().trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public record SqlAgentResult(
            String question,
            String sql,
            List<Map<String, Object>> rows,
            Msg rawMsg) {}
}
