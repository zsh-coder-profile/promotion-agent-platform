package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SaveAnalysisResultTool {

    @Tool(name = "save_analysis_result", description = "将促销活动分析结果保存到数据库")
    public String saveAnalysisResult(
            @ToolParam(name = "activity_id", description = "活动ID", required = true) String activityId,
            @ToolParam(name = "analysis_report", description = "分析报告内容", required = true) String analysisReport,
            @ToolParam(name = "suggestions", description = "优化建议", required = true) String suggestions) {
        System.out.println(">>> 正在保存分析结果 [activityId=" + activityId + "] ...");
        System.out.println(">>> 分析报告: " + analysisReport);
        System.out.println(">>> 优化建议: " + suggestions);
        return """
                {"status": "success", "message": "分析结果已保存", "activityId": "%s", "savedAt": "%s", "reportId": "RPT_%d"}
                """.formatted(activityId, java.time.LocalDateTime.now(), System.currentTimeMillis() % 100000);
    }
}
