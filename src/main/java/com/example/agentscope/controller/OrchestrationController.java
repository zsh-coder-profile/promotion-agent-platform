package com.example.agentscope.controller;

import com.example.agentscope.service.OrchestrationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orchestration")
public class OrchestrationController {

    private final OrchestrationService orchestrationService;

    public OrchestrationController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * 菜品推荐编排
     * POST /api/orchestration/dish-recommend?userId=user_001
     */
    @PostMapping("/dish-recommend")
    public Map<String, Object> dishRecommend(@RequestParam String userId) {
        return orchestrationService.runDishRecommendation(userId);
    }

    /**
     * 促销活动分析编排
     * POST /api/orchestration/promotion-analysis?activityId=activity_001
     */
    @PostMapping("/promotion-analysis")
    public Map<String, Object> promotionAnalysis(@RequestParam String activityId) {
        return orchestrationService.runPromotionAnalysis(activityId);
    }
}
