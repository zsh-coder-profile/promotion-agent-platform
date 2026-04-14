package com.example.agentscope.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OrchestrationService {

    private final SequentialAgent dishRecommendPipeline;
    private final SequentialAgent promotionAnalysisPipeline;

    public OrchestrationService(
            @Qualifier("dishRecommendPipeline") SequentialAgent dishRecommendPipeline,
            @Qualifier("promotionAnalysisPipeline") SequentialAgent promotionAnalysisPipeline) {
        this.dishRecommendPipeline = dishRecommendPipeline;
        this.promotionAnalysisPipeline = promotionAnalysisPipeline;
    }

    public Map<String, Object> runDishRecommendation(String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", "dish_recommend");
        result.put("userId", userId);

        try {
            Optional<OverAllState> stateOpt = dishRecommendPipeline.invoke(userId);
            if (stateOpt.isPresent()) {
                OverAllState state = stateOpt.get();
                result.put("status", "success");
                result.put("userProfile", extractText(state.value("user_profile")));
                result.put("recommendation", extractText(state.value("recommendation")));
                result.put("saveResult", extractText(state.value("save_result")));
            } else {
                result.put("status", "error");
                result.put("message", "Pipeline 返回空结果");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public Map<String, Object> runPromotionAnalysis(String activityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", "promotion_analysis");
        result.put("activityId", activityId);

        try {
            Optional<OverAllState> stateOpt = promotionAnalysisPipeline.invoke(activityId);
            if (stateOpt.isPresent()) {
                OverAllState state = stateOpt.get();
                result.put("status", "success");
                result.put("promotionData", extractText(state.value("promotion_data")));
                result.put("analysisResult", extractText(state.value("analysis_result")));
                result.put("saveResult", extractText(state.value("save_result")));
            } else {
                result.put("status", "error");
                result.put("message", "Pipeline 返回空结果");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    private String extractText(Object value) {
        if (value == null) return "";
        return value.toString();
    }
}
