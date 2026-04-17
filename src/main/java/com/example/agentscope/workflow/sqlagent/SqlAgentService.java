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
    public SqlAgentResult run(String question) {
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(question).build();
        Msg responseMsg = sqlAgent.call(userMsg).block();
        String rawText = responseMsg != null ? responseMsg.getTextContent() : "";
        String sql = extractSql(rawText);
        List<Map<String, Object>> rows = sqlTools.executeQuery(sql);

        return new SqlAgentResult(question, sql, rows, responseMsg);
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
