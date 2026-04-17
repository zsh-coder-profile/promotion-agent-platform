package com.example.agentscope.controller;

import com.example.agentscope.workflow.sqlagent.SqlAgentService;
import com.example.agentscope.workflow.sqlagent.SqlAgentService.SqlAgentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sql")
@ConditionalOnProperty(name = "workflow.sql.enabled", havingValue = "true")
public class SqlAgentController {

    private final SqlAgentService sqlAgentService;

    public SqlAgentController(SqlAgentService sqlAgentService) {
        this.sqlAgentService = sqlAgentService;
    }

    /**
     * POST /api/sql/query
     * Body: { "question": "哪个流派的平均曲目时长最长？" }
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);

        try {
            SqlAgentResult agentResult = sqlAgentService.run(question);
            result.put("status", "success");
            result.put("sql", agentResult.sql());
            result.put("rows", agentResult.rows());
            result.put("rowCount", agentResult.rows().size());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
