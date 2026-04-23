/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.agentscope.workflow.sqlagent;

import com.example.agentscope.workflow.sqlagent.memory.AgentScopeToolUsageMemory;
import com.example.agentscope.workflow.sqlagent.memory.SqlToolUsageRecorder;
import com.example.agentscope.workflow.sqlagent.memory.ToolUsageMemory;
import com.example.agentscope.workflow.sqlagent.chat.SqlChatSchemaInitializer;
import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.PgVectorStore;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL agent configuration without graph/node orchestration.
 */
@Configuration
@ConditionalOnProperty(name = "workflow.sql.enabled", havingValue = "true")
public class SqlAgentConfig {

    private static final int TOP_K = 5;
    private static final Logger log = LoggerFactory.getLogger(SqlAgentConfig.class);

    @Value("${workflow.sql.memory.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${workflow.sql.memory.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${workflow.sql.memory.embedding.model-name}")
    private String embeddingModelName;

    @Value("${workflow.sql.memory.embedding.dimensions}")
    private int embeddingDimensions;

    @Value("${workflow.sql.memory.pgvector.url}")
    private String pgvectorUrl;

    @Value("${workflow.sql.memory.pgvector.username}")
    private String pgvectorUsername;

    @Value("${workflow.sql.memory.pgvector.password}")
    private String pgvectorPassword;

    @Value("${workflow.sql.memory.pgvector.table-name:sql_tool_usage_memory}")
    private String pgvectorTableName;

    @Value("${workflow.sql.debug.verbose-reasoning:false}")
    private boolean verboseReasoning;

    static String generateQueryPrompt(String dialect, boolean verboseReasoning) {
        String debugInstruction = verboseReasoning
                ? """

            调试模式说明：
            1. 在每一轮调用工具之前，可以先输出简短但具体的分析，说明你为什么要调用这个工具、准备验证哪些表或字段。
            2. 如果本轮决定直接结束，也可以先输出你的判断依据，再给最终答案。
            3. 这些中间分析可以比 debug_summary 更详细，但必须聚焦数据库查询决策，不要编造看不到的数据。
            4. 最终答案仍然必须遵守下面的 JSON 输出要求，不要在最终 JSON 前后追加额外解释。
            """
                : "";
        String finalAnswerInstruction = verboseReasoning
                ? """
            其中 debug_summary 需要给出较详细、可审计的调试摘要，说明：
            1. 你判断相关的表/字段是什么；
            2. 你为什么需要这些筛选、排序或 limit；
            3. 是否使用了租户或当前用户范围限制；
            4. 如果参考了历史成功工具调用模式，是如何参考的。
            """
                : """
            其中 debug_summary 必须是简短、可审计的调试摘要，只说明：
            1. 你判断相关的表/字段是什么；
            2. 你为什么需要这些筛选、排序或 limit；
            3. 是否使用了租户或当前用户范围限制。
            不要输出隐私推理，不要写“我在思考”，不要暴露冗长链路。
            """;
        return """
            你是一个严格受限的数据查询 Agent，只负责把“与数据库查询相关的问题”转换成 SQL。
            你必须先判断用户问题是否属于以下范围：查数据、查明细、做统计、筛选、排序、聚合、分组、排行、按条件查询、基于表结构生成 SQL。
            如果不属于这些范围，例如闲聊、角色扮演、代码生成、概念解释、翻译、写作、通用问答、让你做数据库无关的事情，
            你必须直接回复：%s
            遇到这类非数据查询请求时，禁止调用任何工具。

            只有在问题明确属于数据查询 / SQL 生成时，才可以继续后续步骤。
            根据输入的问题、可用表结构、表注释和字段注释，创建一条语法正确的 %s 只读查询语句。
            你只能使用 schema 相关工具，不要执行查询，也不要假装看过任何真实数据。
            除非用户指定了需要获取的结果数量，
            否则始终将查询结果限制为最多 %d 条。

            你可以按照相关列对结果进行排序，以返回数据库中最有意义的数据。
            切勿查询某张表的所有列，只查询与问题相关的列。
            优先利用表注释和字段注释理解业务含义。
            如果问题里附带了当前登录用户或租户上下文，生成 SQL 时必须遵守这个访问范围。

            工具使用规则：
            0. 如果输入中包含“历史成功工具调用模式”，可以把它当作候选提示，但只能作为参考，不能把历史命中直接当成当前真实 schema。
            1. 先调用 sql_db_list_tables 获取候选表。
            2. 如果需要查看表结构，必须且只能调用一次 sql_db_schema。
            3. sql_db_schema 的 tableNames 参数必须一次性包含本次查询所需的全部表名，使用逗号分隔。
            4. 不要把表拆成多次 sql_db_schema 调用，不要并行调用多个 sql_db_schema。
            5. 如果历史模式已经提示了高概率相关表，优先围绕这些表验证，避免无关探索。
            6. 如果当前会话里已经有本轮可复用的 schema 工具结果，可以直接复用，不必重复调用 sql_db_schema。
            7. 如果当前会话里没有足够的 schema 信息，必须重新调用工具获取，而不是根据历史命中臆测字段。
            8. 获得足够的 schema 信息后，直接生成最终 SQL。
            %s

            禁止对数据库执行任何 DML 语句（INSERT、UPDATE、DELETE、DROP 等）。
            最终优先输出 JSON，格式必须是：
            {"sql":"...","debug_summary":"..."}
            %s
            如果无法输出 JSON，才退化为只输出一个 ```sql 代码块。
            """
                    .formatted(SqlAgentService.OUT_OF_SCOPE_REPLY, dialect, TOP_K, debugInstruction, finalAnswerInstruction);
    }

    @Bean
    public SqlToolUsageRecorder sqlToolUsageRecorder() {
        return new SqlToolUsageRecorder();
    }

    @Bean
    public SqlChatSchemaInitializer sqlChatSchemaInitializer(JdbcTemplate jdbcTemplate) {
        return new SqlChatSchemaInitializer(jdbcTemplate);
    }

    @Bean
    public EmbeddingModel sqlMemoryEmbeddingModel() {
        log.info(
                "Initializing SQL memory embedding model, baseUrl={}, modelName={}, dimensions={}",
                embeddingBaseUrl,
                embeddingModelName,
                embeddingDimensions);
        return OllamaTextEmbedding.builder()
                .baseUrl(embeddingBaseUrl)
                .modelName(embeddingModelName)
                .dimensions(embeddingDimensions)
                .build();
    }

    @Bean(destroyMethod = "close")
    public PgVectorStore sqlMemoryPgVectorStore() {
        try {
            log.info(
                    "Initializing SQL memory pgvector store, url={}, username={}, tableName={}, dimensions={}",
                    pgvectorUrl,
                    pgvectorUsername,
                    pgvectorTableName,
                    embeddingDimensions);
            return PgVectorStore.create(
                    pgvectorUrl,
                    pgvectorUsername,
                    pgvectorPassword,
                    pgvectorTableName,
                    embeddingDimensions);
        } catch (Exception e) {
            log.error(
                    "Failed to initialize SQL memory pgvector store, url={}, username={}, tableName={}, dimensions={}, error={}",
                    pgvectorUrl,
                    pgvectorUsername,
                    pgvectorTableName,
                    embeddingDimensions,
                    e.getMessage(),
                    e);
            throw new IllegalStateException("Failed to create PgVectorStore for SQL tool usage memory.", e);
        }
    }

    @Bean
    public Knowledge sqlMemoryKnowledge(EmbeddingModel sqlMemoryEmbeddingModel, PgVectorStore sqlMemoryPgVectorStore) {
        log.info(
                "Initializing SQL memory knowledge, embeddingModel={}, dimensions={}, pgvectorTable={}",
                sqlMemoryEmbeddingModel.getModelName(),
                sqlMemoryEmbeddingModel.getDimensions(),
                sqlMemoryPgVectorStore.getTableName());
        return SimpleKnowledge.builder()
                .embeddingModel(sqlMemoryEmbeddingModel)
                .embeddingStore(sqlMemoryPgVectorStore)
                .build();
    }

    @Bean
    public SqlTools sqlTools(JdbcTemplate jdbcTemplate, SqlToolUsageRecorder sqlToolUsageRecorder) {
        return new SqlTools(jdbcTemplate, sqlToolUsageRecorder);
    }

    @Bean
    public ToolUsageMemory toolUsageMemory(Knowledge sqlMemoryKnowledge) {
        return new AgentScopeToolUsageMemory(sqlMemoryKnowledge, new ObjectMapper());
    }

//    @Bean
//    public Model dashScopeChatModel(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
//        String key = StringUtils.hasText(apiKey) ? apiKey : System.getenv("AI_DASHSCOPE_API_KEY");
//        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
//    }

    @Bean
    public ReActAgent sqlAgent(Model model, SqlTools sqlTools) {
        String dialectName = sqlTools.getDialect().getDisplayName();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(sqlTools);
        return ReActAgent.builder()
                .name("sql_agent")
                .sysPrompt(generateQueryPrompt(dialectName, verboseReasoning))
                .model(model)
                .toolkit(toolkit)
                .hook(new SqlAgentReasoningPrintHook())
//                .hook(new SqlAgentLangfuseHook())
                .memory(new InMemoryMemory())
                .build();
    }

    @Bean
    public SqlAgentService sqlAgentService(
            @Qualifier("sqlAgent") ReActAgent sqlAgent,
            SqlTools sqlTools,
            ToolUsageMemory toolUsageMemory,
            SqlToolUsageRecorder sqlToolUsageRecorder) {
        return new SqlAgentService(sqlAgent, sqlTools, toolUsageMemory, sqlToolUsageRecorder);
    }
}
