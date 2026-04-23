package com.example.agentscope.workflow.sqlagent.chat;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

public class SqlChatSchemaInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public SqlChatSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sql_chat_conversation (
                    id VARCHAR(64) PRIMARY KEY,
                    owner_user_id VARCHAR(128) NOT NULL,
                    owner_tenant_id VARCHAR(128),
                    owner_role VARCHAR(32) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_question TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sql_chat_conversation_owner_updated
                ON sql_chat_conversation (owner_user_id, owner_role, updated_at DESC)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sql_chat_message (
                    id VARCHAR(64) PRIMARY KEY,
                    conversation_id VARCHAR(64) NOT NULL,
                    message_role VARCHAR(16) NOT NULL,
                    question TEXT,
                    answer_text TEXT,
                    sql_text TEXT,
                    debug_summary TEXT,
                    result_rows_json TEXT,
                    result_row_count INTEGER,
                    status VARCHAR(16) NOT NULL,
                    error_message TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_sql_chat_message_conversation
                        FOREIGN KEY (conversation_id) REFERENCES sql_chat_conversation (id)
                        ON DELETE CASCADE
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE sql_chat_message
                ADD COLUMN IF NOT EXISTS debug_summary TEXT
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sql_chat_message_conversation_created
                ON sql_chat_message (conversation_id, created_at ASC)
                """);
    }
}
