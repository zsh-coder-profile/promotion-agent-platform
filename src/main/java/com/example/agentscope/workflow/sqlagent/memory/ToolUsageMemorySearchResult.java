package com.example.agentscope.workflow.sqlagent.memory;

import java.time.OffsetDateTime;
import java.util.Map;

public record ToolUsageMemorySearchResult(
        String question,
        String toolName,
        Map<String, Object> args,
        double similarity,
        OffsetDateTime createdAt) {}
