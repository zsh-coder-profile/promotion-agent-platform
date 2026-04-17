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
package com.example.agentscope.workflow.sqlagent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * SQL tools for the SQL agent: list tables and get metadata for SQL planning.
 * Automatically detects the database dialect (H2 / MySQL / PostgreSQL) from the DataSource JDBC URL.
 */
public final class SqlTools {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseDialect dialect;

    public SqlTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = detectDialect(jdbcTemplate.getDataSource());
    }

    public SqlTools(JdbcTemplate jdbcTemplate, DatabaseDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    @Tool(
            name = "sql_db_list_tables",
            description = "输入为空字符串，输出为数据库中所有表名的逗号分隔列表。"
    )
    public String listTables(
            @ToolParam(name = "ignored", description = "空字符串", required = false)
            String ignored) {
        List<String> tables =
                jdbcTemplate.queryForList(dialect.listTablesSql(), String.class);
        return String.join(", ", tables);
    }

    @Tool(
            name = "sql_db_schema",
            description = "输入为逗号分隔的表名列表，输出为这些表的结构定义、表注释和字段注释，不包含真实数据。"
    )
    public String getSchema(
            @ToolParam(name = "tableNames", description = "逗号分隔的表名")
            String tableNames) {
        String[] tables = tableNames.split(",");
        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            String t = table.trim();
            if (t.isEmpty()) continue;
            try {
                String normalizedName = dialect.normalizeTableName(t);
                String tableComment = getTableComment(normalizedName);
                sb.append("TABLE \"").append(t).append("\"");
                if (!tableComment.isBlank()) {
                    sb.append(" COMMENT '").append(tableComment).append("'");
                }
                sb.append("\n");
                sb.append("COLUMNS (\n");
                List<Map<String, Object>> columns =
                        jdbcTemplate.queryForList(dialect.columnsSql(), normalizedName);
                sb.append(
                        columns.stream()
                                .map(this::formatColumnMetadata)
                                .collect(Collectors.joining(",\n")));
                sb.append("\n)\n\n");
                sb.append("\n");
            } catch (Exception e) {
                sb.append("Error for table ")
                        .append(t)
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    public List<Map<String, Object>> executeQuery(String query) {
        validateReadOnlyQuery(query);
        try {
            return jdbcTemplate.queryForList(query);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute SQL query: " + e.getMessage(), e);
        }
    }

    private String getTableComment(String normalizedTableName) {
        try {
            String comment = jdbcTemplate.queryForObject(
                    dialect.tableCommentSql(), String.class, normalizedTableName);
            return comment == null ? "" : comment;
        } catch (Exception e) {
            return "";
        }
    }

    private String formatColumnMetadata(Map<String, Object> column) {
        String columnName = stringValue(column.get("COLUMN_NAME"));
        String dataType = stringValue(column.get("DATA_TYPE"));
        String comment = stringValue(
                column.getOrDefault("COLUMN_COMMENT", column.get("REMARKS")));
        StringBuilder builder = new StringBuilder();
        builder.append("  \"").append(columnName).append("\" ").append(dataType);
        if (!comment.isBlank()) {
            builder.append(" COMMENT '").append(comment).append("'");
        }
        return builder.toString();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static void validateReadOnlyQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("SQL query must not be blank.");
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }
        if (normalized.contains("INSERT")
                || normalized.contains("UPDATE")
                || normalized.contains("DELETE")
                || normalized.contains("DROP")
                || normalized.contains("ALTER")
                || normalized.contains("TRUNCATE")) {
            throw new IllegalArgumentException("Only read-only SQL queries are allowed.");
        }
    }

    private static DatabaseDialect detectDialect(DataSource dataSource) {
        if (dataSource == null) {
            return DatabaseDialect.H2;
        }
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            return DatabaseDialect.fromJdbcUrl(url);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect database dialect from DataSource", e);
        }
    }
}
