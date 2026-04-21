package com.example.agentscope.workflow.sqlagent;

import com.example.agentscope.workflow.sqlagent.memory.NoOpToolUsageMemory;
import com.example.agentscope.workflow.sqlagent.memory.RecordedToolUsage;
import com.example.agentscope.workflow.sqlagent.memory.SqlToolUsageRecorder;
import com.example.agentscope.workflow.sqlagent.memory.ToolUsageMemory;
import com.example.agentscope.workflow.sqlagent.memory.ToolUsageMemorySearchResult;
import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that asks the LLM to plan SQL from schema metadata, then executes the SQL locally.
 */
public class SqlAgentService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SQL_BLOCK =
            Pattern.compile("```sql\\s*(.*?)\\s*```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    public static final String OUT_OF_SCOPE_REPLY =
            "我只是一个数据查询 Agent，只能帮助你生成数据库查询 SQL，请提出与数据查询相关的问题。";
    private static final int MEMORY_SEARCH_LIMIT = 3;
    private static final double MEMORY_SIMILARITY_THRESHOLD = 0.2d;
    private static final double MEMORY_SCHEMA_PREFETCH_THRESHOLD = 0.4d;

    private final ReActAgent sqlAgent;
    private final ReActAgent sqlDirectAgent;
    private final SqlTools sqlTools;
    private final ToolUsageMemory toolUsageMemory;
    private final SqlToolUsageRecorder sqlToolUsageRecorder;

    public SqlAgentService(ReActAgent sqlAgent, SqlTools sqlTools) {
        this(sqlAgent, sqlAgent, sqlTools, new NoOpToolUsageMemory(), new SqlToolUsageRecorder());
    }

    public SqlAgentService(
            ReActAgent sqlAgent,
            ReActAgent sqlDirectAgent,
            SqlTools sqlTools,
            ToolUsageMemory toolUsageMemory,
            SqlToolUsageRecorder sqlToolUsageRecorder) {
        this.sqlAgent = sqlAgent;
        this.sqlDirectAgent = sqlDirectAgent;
        this.sqlTools = sqlTools;
        this.toolUsageMemory = toolUsageMemory;
        this.sqlToolUsageRecorder = sqlToolUsageRecorder;
    }

    /**
     * Plan SQL with the model and execute it locally without sending query results back to the LLM.
     */
    public SqlAgentResult run(String question, SqlAccessContext accessContext) {
        List<ToolUsageMemorySearchResult> memoryHits = toolUsageMemory.searchSimilarUsage(
                question, accessContext, MEMORY_SEARCH_LIMIT, MEMORY_SIMILARITY_THRESHOLD);
        SchemaPrefetch schemaPrefetch = findSchemaPrefetch(memoryHits);
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(buildQuestionWithAccessContext(question, accessContext, memoryHits, schemaPrefetch))
                .build();
        if (schemaPrefetch != null) {
            return runWithPrefetchedSchema(question, accessContext, memoryHits, schemaPrefetch, userMsg);
        }
        return runWithTools(question, accessContext, memoryHits, userMsg);
    }

    private SqlAgentResult runWithTools(
            String question,
            SqlAccessContext accessContext,
            List<ToolUsageMemorySearchResult> memoryHits,
            Msg userMsg) {
        String recorderSessionId = sqlToolUsageRecorder.startSession();
        try {
            Msg responseMsg = sqlAgent.call(userMsg).block();
            String rawText = responseMsg != null ? responseMsg.getTextContent() : "";
            if (isOutOfScopeReply(rawText)) {
                List<RecordedToolUsage> recordedToolUsages = sqlToolUsageRecorder.stopSession(recorderSessionId);
                return new SqlAgentResult(
                        question,
                        "",
                        List.of(),
                        responseMsg,
                        OUT_OF_SCOPE_REPLY,
                        memoryHits,
                        !memoryHits.isEmpty(),
                        false,
                        recordedToolUsages);
            }
            String sql = extractSql(rawText);
            List<Map<String, Object>> rows = sqlTools.executeQuery(sql, accessContext);
            List<RecordedToolUsage> recordedToolUsages = sqlToolUsageRecorder.stopSession(recorderSessionId);
            toolUsageMemory.saveSuccessfulUsage(question, accessContext, recordedToolUsages, sql);

            return new SqlAgentResult(
                    question,
                    sql,
                    rows,
                    responseMsg,
                    "",
                    memoryHits,
                    !memoryHits.isEmpty(),
                    !recordedToolUsages.isEmpty(),
                    recordedToolUsages);
        } catch (RuntimeException e) {
            sqlToolUsageRecorder.clearSession(recorderSessionId);
            throw e;
        }
    }

    private SqlAgentResult runWithPrefetchedSchema(
            String question,
            SqlAccessContext accessContext,
            List<ToolUsageMemorySearchResult> memoryHits,
            SchemaPrefetch schemaPrefetch,
            Msg userMsg) {
        Msg responseMsg = sqlDirectAgent.call(userMsg).block();
        String rawText = responseMsg != null ? responseMsg.getTextContent() : "";
        if (isOutOfScopeReply(rawText)) {
            return new SqlAgentResult(
                    question,
                    "",
                    List.of(),
                    responseMsg,
                    OUT_OF_SCOPE_REPLY,
                    memoryHits,
                    !memoryHits.isEmpty(),
                    false,
                    List.of());
        }
        String sql = extractSql(rawText);
        List<Map<String, Object>> rows = sqlTools.executeQuery(sql, accessContext);
        List<RecordedToolUsage> syntheticToolUsages = List.of(
                new RecordedToolUsage("sql_db_schema", Map.of("tableNames", schemaPrefetch.tableNames())));
        toolUsageMemory.saveSuccessfulUsage(question, accessContext, syntheticToolUsages, sql);
        return new SqlAgentResult(
                question,
                sql,
                rows,
                responseMsg,
                "",
                memoryHits,
                !memoryHits.isEmpty(),
                true,
                syntheticToolUsages);
    }

    static String buildQuestionWithAccessContext(
            String question,
            SqlAccessContext accessContext,
            List<ToolUsageMemorySearchResult> memoryHits,
            SchemaPrefetch schemaPrefetch) {
        StringBuilder builder = new StringBuilder();
        builder.append(question == null ? "" : question);
        if (memoryHits != null && !memoryHits.isEmpty()) {
            builder.append("\n\n[历史成功工具调用模式]\n")
                    .append(formatMemoryHits(memoryHits));
        }
        if (schemaPrefetch != null) {
            builder.append("\n\n[最新 schema 上下文]\n")
                    .append("以下 schema 已根据历史命中的高相似表集合重新拉取，属于当前请求可直接使用的最新结构。\n")
                    .append("禁止再次调用 schema 工具，直接基于这些表生成最终 SQL。\n")
                    .append(schemaPrefetch.schemaText());
        }
        if (accessContext == null) {
            return builder.toString();
        }
        if (accessContext.isAdmin()) {
            builder.append("\n\n[系统上下文]\n当前登录用户是平台管理员，可以查看全量数据。");
            return builder.toString();
        }
        builder.append("\n\n[系统上下文]\n当前登录用户: ")
                .append(accessContext.userId())
                .append("\n当前租户: ")
                .append(accessContext.tenantId())
                .append("\n生成 SQL 时，所有业务表都必须使用 tenant_id = '")
                .append(accessContext.tenantId())
                .append("' 做过滤。");
        return builder.toString();
    }

    private static String formatMemoryHits(List<ToolUsageMemorySearchResult> memoryHits) {
        return memoryHits.stream()
                .map(hit -> "- 问题: %s | 工具: %s | 参数: %s | 相似度: %.2f"
                        .formatted(hit.question(), hit.toolName(), hit.args(), hit.similarity()))
                .collect(Collectors.joining("\n"));
    }

    private SchemaPrefetch findSchemaPrefetch(List<ToolUsageMemorySearchResult> memoryHits) {
        if (memoryHits == null) {
            return null;
        }
        for (ToolUsageMemorySearchResult hit : memoryHits) {
            if (!"sql_db_schema".equals(hit.toolName())) {
                continue;
            }
            if (hit.similarity() < MEMORY_SCHEMA_PREFETCH_THRESHOLD) {
                continue;
            }
            Object tableNamesValue = hit.args().get("tableNames");
            if (tableNamesValue == null) {
                continue;
            }
            String tableNames = tableNamesValue.toString().trim();
            if (tableNames.isBlank()) {
                continue;
            }
            String schemaText = sqlTools.getSchema(tableNames);
            return new SchemaPrefetch(tableNames, schemaText, hit.similarity());
        }
        return null;
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

    private static boolean isOutOfScopeReply(String rawText) {
        return rawText != null && OUT_OF_SCOPE_REPLY.equals(rawText.trim());
    }

    public record SqlAgentResult(
            String question,
            String sql,
            List<Map<String, Object>> rows,
            Msg rawMsg,
            String message,
            List<ToolUsageMemorySearchResult> memoryHits,
            boolean memoryApplied,
            boolean memorySaved,
            List<RecordedToolUsage> recordedToolUsages) {
        public int memoryHitCount() {
            return memoryHits == null ? 0 : memoryHits.size();
        }
    }

    record SchemaPrefetch(
            String tableNames,
            String schemaText,
            double similarity) {}
}
