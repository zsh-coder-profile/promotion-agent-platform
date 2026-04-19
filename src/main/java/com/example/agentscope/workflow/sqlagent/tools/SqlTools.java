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

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL tools for the SQL agent: list tables and get metadata for SQL planning.
 * Automatically detects the database dialect (H2 / MySQL / PostgreSQL) from the DataSource JDBC URL.
 */
public final class SqlTools {
    private static final Pattern WHERE_PATTERN = Pattern.compile("(?i)\\bwhere\\b");
    private static final Pattern TRAILING_CLAUSE_PATTERN =
            Pattern.compile("(?i)\\b(group\\s+by|order\\s+by|limit|offset|fetch)\\b");
    private static final Pattern TABLE_REFERENCE_PATTERN =
            Pattern.compile("(?i)\\b(?:from|join)\\s+([a-zA-Z_][\\w.]*)"
                    + "(?:\\s+(?:as\\s+)?([a-zA-Z_][\\w]*))?");
    private static final Pattern TENANT_LITERAL_PATTERN =
            Pattern.compile("(?i)\\b(?:[a-zA-Z_][\\w]*\\s*\\.\\s*)?tenant_id\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\"|([0-9]+))");
    private static final Set<String> SQL_KEYWORDS =
            Set.of("where", "join", "left", "right", "inner", "outer", "full", "cross",
                    "on", "group", "order", "limit", "offset", "fetch", "union");

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

    public List<Map<String, Object>> executeQuery(String query, SqlAccessContext accessContext) {
        validateReadOnlyQuery(query);
        ScopedQuery scopedQuery = applyTenantIsolation(query, accessContext);
        try {
            if (scopedQuery.args().length == 0) {
                return jdbcTemplate.queryForList(scopedQuery.sql());
            }
            return jdbcTemplate.queryForList(scopedQuery.sql(), scopedQuery.args());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute SQL query: " + e.getMessage(), e);
        }
    }

    static ScopedQuery applyTenantIsolation(String query, SqlAccessContext accessContext) {
        if (accessContext == null || !accessContext.requiresTenantIsolation()) {
            return new ScopedQuery(query, new Object[0]);
        }
        if (accessContext.tenantId() == null || accessContext.tenantId().isBlank()) {
            throw new IllegalArgumentException("Tenant id is required for isolated queries.");
        }
        if (containsTenantFilter(query)) {
            validateTenantScope(query, accessContext.tenantId());
            return new ScopedQuery(query, new Object[0]);
        }

        String topLevelSql = maskNestedScopes(query);
        List<String> qualifiers = TABLE_REFERENCE_PATTERN.matcher(topLevelSql)
                .results()
                .map(SqlTools::resolveQualifier)
                .distinct()
                .toList();
        if (qualifiers.isEmpty()) {
            throw new IllegalArgumentException("Unable to infer tenant-aware tables from SQL.");
        }

        String tenantCondition = qualifiers.stream()
                .map(alias -> alias + ".tenant_id = ?")
                .collect(Collectors.joining(" AND "));

        MatchResult trailingClause = TRAILING_CLAUSE_PATTERN.matcher(topLevelSql).results().findFirst().orElse(null);
        int clauseStart = trailingClause == null ? query.length() : trailingClause.start();
        String head = query.substring(0, clauseStart).trim();
        String tail = query.substring(clauseStart);
        String suffix = tail.isBlank() ? "" : " " + tail.trim();
        String scopedSql;
        if (WHERE_PATTERN.matcher(maskNestedScopes(head)).find()) {
            scopedSql = head + " AND " + tenantCondition + suffix;
        } else {
            scopedSql = head + " WHERE " + tenantCondition + suffix;
        }
        Object[] args = qualifiers.stream().map(alias -> accessContext.tenantId()).toArray();
        return new ScopedQuery(scopedSql, args);
    }

    private static boolean containsTenantFilter(String query) {
        return TENANT_LITERAL_PATTERN.matcher(query).find();
    }

    private static void validateTenantScope(String query, String tenantId) {
        Matcher matcher = TENANT_LITERAL_PATTERN.matcher(query);
        while (matcher.find()) {
            String sqlTenantId = firstNonBlank(matcher.group(1), matcher.group(2), matcher.group(3));
            if (sqlTenantId != null && !tenantId.equals(sqlTenantId.trim())) {
                throw new IllegalArgumentException("SQL tenant scope does not match current user tenant.");
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String maskNestedScopes(String sql) {
        StringBuilder masked = new StringBuilder(sql.length());
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !inDoubleQuote && !isEscaped(sql, i)) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote && !isEscaped(sql, i)) {
                inDoubleQuote = !inDoubleQuote;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')' && depth > 0) {
                    depth--;
                    masked.append(depth == 0 ? ch : ' ');
                    continue;
                }
            }
            masked.append(depth > 0 ? ' ' : ch);
        }
        return masked.toString();
    }

    private static boolean isEscaped(String sql, int index) {
        return index > 0 && sql.charAt(index - 1) == '\\';
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

    private static String resolveQualifier(MatchResult result) {
        String tableName = result.group(1);
        String alias = result.groupCount() >= 2 ? result.group(2) : null;
        if (alias == null || SQL_KEYWORDS.contains(alias.toLowerCase(Locale.ROOT))) {
            int dotIndex = tableName.lastIndexOf('.');
            return dotIndex >= 0 ? tableName.substring(dotIndex + 1) : tableName;
        }
        return alias;
    }

    record ScopedQuery(String sql, Object[] args) {}

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
