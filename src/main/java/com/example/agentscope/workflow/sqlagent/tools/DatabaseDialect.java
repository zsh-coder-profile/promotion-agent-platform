package com.example.agentscope.workflow.sqlagent.tools;

/**
 * Database dialect abstraction. Encapsulates dialect-specific SQL for table listing and metadata lookup.
 */
public enum DatabaseDialect {

    H2("H2") {
        @Override
        public String listTablesSql() {
            return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_SCHEMA = 'haagen_dazs' AND TABLE_TYPE = 'BASE TABLE'";
        }

        @Override
        public String columnsSql() {
            return "SELECT COLUMN_NAME, TYPE_NAME AS DATA_TYPE, REMARKS AS COLUMN_COMMENT"
                    + " FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        }

        @Override
        public String normalizeTableName(String tableName) {
            return tableName.toUpperCase();
        }

        @Override
        public String tableCommentSql() {
            return "SELECT REMARKS AS TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?";
        }
    },

    MYSQL("MySQL") {
        @Override
        public String listTablesSql() {
            return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'";
        }

        @Override
        public String columnsSql() {
            return "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT"
                    + " FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
                    + " ORDER BY ORDINAL_POSITION";
        }

        @Override
        public String normalizeTableName(String tableName) {
            return tableName;
        }

        @Override
        public String tableCommentSql() {
            return "SELECT TABLE_COMMENT"
                    + " FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        }
    },

    POSTGRESQL("PostgreSQL") {
        @Override
        public String listTablesSql() {
            return "SELECT tablename AS TABLE_NAME FROM pg_catalog.pg_tables"
                    + " WHERE schemaname = 'haagen_dazs'";
        }

        @Override
        public String columnsSql() {
            return "SELECT cols.column_name AS COLUMN_NAME, cols.data_type AS DATA_TYPE,"
                    + " pgd.description AS COLUMN_COMMENT"
                    + " FROM information_schema.columns"
                    + " cols"
                    + " LEFT JOIN pg_catalog.pg_statio_all_tables st"
                    + " ON st.schemaname = cols.table_schema AND st.relname = cols.table_name"
                    + " LEFT JOIN pg_catalog.pg_description pgd"
                    + " ON pgd.objoid = st.relid AND pgd.objsubid = cols.ordinal_position"
                    + " WHERE cols.table_schema = 'haagen_dazs' AND cols.table_name = ?"
                    + " ORDER BY cols.ordinal_position";
        }

        @Override
        public String normalizeTableName(String tableName) {
            return tableName.toLowerCase();
        }

        @Override
        public String tableCommentSql() {
            return "SELECT d.description AS TABLE_COMMENT"
                    + " FROM pg_catalog.pg_class c"
                    + " JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace"
                    + " LEFT JOIN pg_catalog.pg_description d ON d.objoid = c.oid AND d.objsubid = 0"
                    + " WHERE n.nspname = 'haagen_dazs' AND c.relname = ?";
        }
    };

    private final String displayName;

    DatabaseDialect(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract String listTablesSql();

    public abstract String columnsSql();

    public abstract String normalizeTableName(String tableName);

    public abstract String tableCommentSql();

    /**
     * Detect dialect from a JDBC URL string.
     */
    public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL must not be null");
        }
        String lower = jdbcUrl.toLowerCase();
        if (lower.startsWith("jdbc:mysql") || lower.startsWith("jdbc:mariadb")) {
            return MYSQL;
        }
        if (lower.startsWith("jdbc:postgresql")) {
            return POSTGRESQL;
        }
        if (lower.startsWith("jdbc:h2")) {
            return H2;
        }
        throw new UnsupportedOperationException("Unsupported database dialect for URL: " + jdbcUrl);
    }
}
