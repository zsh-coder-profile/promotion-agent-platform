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
import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
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

    static String generateQueryPrompt(String dialect) {
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
            0. 如果输入中包含“历史成功工具调用模式”，可以把它当作候选提示，但只能作为参考，仍然必须以当前工具返回的最新 schema 为准。
            1. 先调用 sql_db_list_tables 获取候选表。
            2. 如果需要查看表结构，必须且只能调用一次 sql_db_schema。
            3. sql_db_schema 的 tableNames 参数必须一次性包含本次查询所需的全部表名，使用逗号分隔。
            4. 不要把表拆成多次 sql_db_schema 调用，不要并行调用多个 sql_db_schema。
            5. 如果历史模式已经提示了高概率相关表，优先围绕这些表验证，避免无关探索。
            6. 获得足够的 schema 信息后，直接生成最终 SQL。

            禁止对数据库执行任何 DML 语句（INSERT、UPDATE、DELETE、DROP 等）。
            最终只输出 SQL，不要输出解释。可以输出 JSON：{"sql":"..."}，也可以只输出一个 ```sql 代码块。
            """
                    .formatted(SqlAgentService.OUT_OF_SCOPE_REPLY, dialect, TOP_K);
    }

    static String generateDirectQueryPrompt(String dialect) {
        return """
            你是一个严格受限的数据查询 Agent，只负责把“与数据库查询相关的问题”转换成 SQL。
            你必须先判断用户问题是否属于以下范围：查数据、查明细、做统计、筛选、排序、聚合、分组、排行、按条件查询、基于表结构生成 SQL。
            如果不属于这些范围，例如闲聊、角色扮演、代码生成、概念解释、翻译、写作、通用问答、让你做数据库无关的事情，
            你必须直接回复：%s

            当前输入里如果已经提供了“最新 schema 上下文”，你必须直接基于该 schema 生成 SQL，不要再请求任何工具，也不要假装知道 schema 之外的信息。
            根据输入的问题、schema 上下文、表注释和字段注释，创建一条语法正确的 %s 只读查询语句。
            除非用户指定了需要获取的结果数量，否则始终将查询结果限制为最多 %d 条。
            切勿查询某张表的所有列，只查询与问题相关的列。
            优先利用表注释和字段注释理解业务含义。
            如果问题里附带了当前登录用户或租户上下文，生成 SQL 时必须遵守这个访问范围。

            禁止对数据库执行任何 DML 语句（INSERT、UPDATE、DELETE、DROP 等）。
            最终只输出 SQL，不要输出解释。可以输出 JSON：{"sql":"..."}，也可以只输出一个 ```sql 代码块。
            """
                .formatted(SqlAgentService.OUT_OF_SCOPE_REPLY, dialect, TOP_K);
    }

    @Bean
    public SqlToolUsageRecorder sqlToolUsageRecorder() {
        return new SqlToolUsageRecorder();
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
                .sysPrompt(generateQueryPrompt(dialectName))
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    @Bean
    public ReActAgent sqlDirectAgent(Model model, SqlTools sqlTools) {
        String dialectName = sqlTools.getDialect().getDisplayName();
        return ReActAgent.builder()
                .name("sql_direct_agent")
                .sysPrompt(generateDirectQueryPrompt(dialectName))
                .model(model)
                .memory(new InMemoryMemory())
                .build();
    }

    @Bean
    public SqlAgentService sqlAgentService(
            @Qualifier("sqlAgent") ReActAgent sqlAgent,
            @Qualifier("sqlDirectAgent") ReActAgent sqlDirectAgent,
            SqlTools sqlTools,
            ToolUsageMemory toolUsageMemory,
            SqlToolUsageRecorder sqlToolUsageRecorder) {
        return new SqlAgentService(sqlAgent, sqlDirectAgent, sqlTools, toolUsageMemory, sqlToolUsageRecorder);
    }
}
