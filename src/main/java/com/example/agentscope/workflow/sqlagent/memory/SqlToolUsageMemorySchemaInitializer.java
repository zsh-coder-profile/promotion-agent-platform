package com.example.agentscope.workflow.sqlagent.memory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

public class SqlToolUsageMemorySchemaInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public SqlToolUsageMemorySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
    }
}
