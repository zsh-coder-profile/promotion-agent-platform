package com.example.agentscope.workflow.sqlagent.memory;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;

import java.util.List;

public interface ToolUsageMemory {

    List<ToolUsageMemorySearchResult> searchSimilarUsage(
            String question,
            SqlAccessContext accessContext,
            int limit,
            double similarityThreshold);

    void saveSuccessfulUsage(
            String question,
            SqlAccessContext accessContext,
            List<RecordedToolUsage> toolUsages,
            String executedSql);
}
