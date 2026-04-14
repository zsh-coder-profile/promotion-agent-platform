package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PromotionDataTool {

    @Tool(name = "get_promotion_data", description = "根据活动ID获取促销活动的详细数据，包含ROI、核销率、参与人数等")
    public String getPromotionData(
            @ToolParam(name = "activity_id", description = "活动ID", required = true) String activityId) {
        System.out.println(">>> 正在获取活动数据 [" + activityId + "] ...");
        return """
                {
                  "activityId": "%s", "name": "春季新品满减活动", "type": "满减",
                  "rule": "满50减10，满100减25", "startDate": "2026-03-01", "endDate": "2026-03-31",
                  "budget": 50000, "actualSpend": 38500, "totalOrders": 2350,
                  "participantCount": 1860, "avgOrderAmount": 78.5,
                  "couponIssued": 5000, "couponUsed": 3200, "redemptionRate": 0.64,
                  "newUserCount": 420, "repeatPurchaseRate": 0.35, "roi": 2.8,
                  "topDishes": ["水煮鱼片", "麻婆豆腐", "宫保鸡丁"],
                  "peakHours": ["11:30-13:00", "17:30-19:00"]
                }
                """.formatted(activityId);
    }
}
