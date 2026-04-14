package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SaveRecommendationTool {

    @Tool(name = "save_recommendation", description = "将菜品推荐结果保存到数据库")
    public String saveRecommendation(
            @ToolParam(name = "user_id", description = "用户ID", required = true) String userId,
            @ToolParam(name = "recommended_dishes", description = "推荐的菜品列表JSON", required = true) String recommendedDishes,
            @ToolParam(name = "reason", description = "推荐理由", required = true) String reason) {
        System.out.println(">>> 正在保存推荐结果 [userId=" + userId + "] ...");
        System.out.println(">>> 推荐菜品: " + recommendedDishes);
        System.out.println(">>> 推荐理由: " + reason);
        return """
                {"status": "success", "message": "推荐结果已保存", "userId": "%s", "savedAt": "%s", "recordId": "REC_%d"}
                """.formatted(userId, java.time.LocalDateTime.now(), System.currentTimeMillis() % 100000);
    }
}
