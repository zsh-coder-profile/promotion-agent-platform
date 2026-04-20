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

import com.example.agentscope.workflow.sqlagent.tools.SqlTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
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

    static String generateQueryPrompt(String dialect) {
        return """
            你是一个用于与 SQL 数据库交互的智能代理。
            根据输入的问题、可用表结构、表注释和字段注释，创建一条语法正确的 %s 只读查询语句。
            你只能使用 schema 相关工具，不要执行查询，也不要假装看过任何真实数据。
            除非用户指定了需要获取的结果数量，
            否则始终将查询结果限制为最多 %d 条。

            你可以按照相关列对结果进行排序，以返回数据库中最有意义的数据。
            切勿查询某张表的所有列，只查询与问题相关的列。
            优先利用表注释和字段注释理解业务含义。
            如果问题里附带了当前登录用户或租户上下文，生成 SQL 时必须遵守这个访问范围。

            工具使用规则：
            1. 先调用 sql_db_list_tables 获取候选表。
            2. 如果需要查看表结构，必须且只能调用一次 sql_db_schema。
            3. sql_db_schema 的 tableNames 参数必须一次性包含本次查询所需的全部表名，使用逗号分隔。
            4. 不要把表拆成多次 sql_db_schema 调用，不要并行调用多个 sql_db_schema。
            5. 获得足够的 schema 信息后，直接生成最终 SQL。

            禁止对数据库执行任何 DML 语句（INSERT、UPDATE、DELETE、DROP 等）。
            最终只输出 SQL，不要输出解释。可以输出 JSON：{"sql":"..."}，也可以只输出一个 ```sql 代码块。
            """
                    .formatted(dialect, TOP_K);
    }

    @Bean
    public SqlTools sqlTools(JdbcTemplate jdbcTemplate) {
        return new SqlTools(jdbcTemplate);
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
    public SqlAgentService sqlAgentService(ReActAgent sqlAgent, SqlTools sqlTools) {
        return new SqlAgentService(sqlAgent, sqlTools);
    }
}
