package com.example.agentscope.workflow.sqlagent.memory;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SqlToolUsageRecorder {

    private final ConcurrentMap<String, List<RecordedToolUsage>> sessions = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeSessionId = new AtomicReference<>();

    public String startSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, Collections.synchronizedList(new ArrayList<>()));
        activeSessionId.set(sessionId);
        return sessionId;
    }

    public List<RecordedToolUsage> stopSession(String sessionId) {
        if (sessionId != null) {
            activeSessionId.compareAndSet(sessionId, null);
        }
        List<RecordedToolUsage> recorded = sessionId == null ? null : sessions.remove(sessionId);
        return recorded == null ? List.of() : List.copyOf(recorded);
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            activeSessionId.compareAndSet(sessionId, null);
            sessions.remove(sessionId);
        }
    }

    public void record(String toolName, Map<String, Object> args) {
        String sessionId = activeSessionId.get();
        List<RecordedToolUsage> recorded = sessionId == null ? null : sessions.get(sessionId);
        if (recorded == null) {
            return;
        }
        recorded.add(new RecordedToolUsage(toolName, new LinkedHashMap<>(args)));
    }
}
