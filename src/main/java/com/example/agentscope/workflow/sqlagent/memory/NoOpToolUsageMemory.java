package com.example.agentscope.workflow.sqlagent.memory;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;

import java.util.List;

public class NoOpToolUsageMemory implements ToolUsageMemory {

    @Override
    public List<ToolUsageMemorySearchResult> searchSimilarUsage(
            String question,
            SqlAccessContext accessContext,
            int limit,
            double similarityThreshold) {
        return List.of();
    }

    @Override
    public void saveSuccessfulUsage(
            String question,
            SqlAccessContext accessContext,
            List<RecordedToolUsage> toolUsages,
            String executedSql) {
        // no-op
    }
}
