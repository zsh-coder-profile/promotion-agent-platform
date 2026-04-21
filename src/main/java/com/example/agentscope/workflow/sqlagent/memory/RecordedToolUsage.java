package com.example.agentscope.workflow.sqlagent.memory;

import java.util.Map;

public record RecordedToolUsage(
        String toolName,
        Map<String, Object> args) {}
