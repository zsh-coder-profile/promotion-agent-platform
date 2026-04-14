package com.example.agentscope.pipeline;

import com.alibaba.cloud.ai.agent.agentscope.AgentScopeAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.example.agentscope.tool.DishQueryTool;
import com.example.agentscope.tool.SaveRecommendationTool;
import com.example.agentscope.tool.UserProfileTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 菜品推荐编排 Pipeline（SDK SequentialAgent）
 * 固定流程：用户画像分析 → 菜品匹配推荐 → 推荐结果落库
 */
@Configuration
public class DishRecommendPipelineConfig {

    @Autowired(required = false)
    private StudioMessageHook studioMessageHook;

    @Bean("dishRecommendPipeline")
    public SequentialAgent dishRecommendPipeline(OpenAIChatModel pipelineModel,
                                                  UserProfileTool userProfileTool,
                                                  DishQueryTool dishQueryTool,
                                                  SaveRecommendationTool saveRecommendationTool) {

        // Step 1: 用户画像分析
        Toolkit profileToolkit = new Toolkit();
        profileToolkit.registerTool(userProfileTool);

        ReActAgent.Builder profileBuilder = ReActAgent.builder()
                .name("用户画像分析师")
                .model(pipelineModel)
                .sysPrompt("""
                        你是一个用户画像分析师。根据提供的用户ID，使用 get_user_profile 工具获取用户画像数据，
                        然后对用户的饮食偏好进行总结分析，输出用户口味偏好、消费习惯和推荐方向。
                        输出格式要清晰、结构化，方便下游推荐引擎使用。""")
                .toolkit(profileToolkit)
                .memory(new InMemoryMemory());
        if (studioMessageHook != null) {
            profileBuilder.hook(studioMessageHook);
        }

        AgentScopeAgent profileAgent = AgentScopeAgent.fromBuilder(profileBuilder)
                .name("用户画像分析师")
                .description("根据用户ID获取并分析用户画像")
                .instruction("请分析用户ID为 {input} 的用户画像")
                .includeContents(false)
                .outputKey("user_profile")
                .build();

        // Step 2: 菜品匹配推荐
        Toolkit dishToolkit = new Toolkit();
        dishToolkit.registerTool(dishQueryTool);

        ReActAgent.Builder dishBuilder = ReActAgent.builder()
                .name("菜品推荐引擎")
                .model(pipelineModel)
                .sysPrompt("""
                        你是一个智能菜品推荐引擎。根据上游提供的用户画像分析结果，
                        使用 query_dishes 工具查询匹配的候选菜品，然后结合用户的口味偏好、价格区间和消费场景，
                        从候选菜品中精选3-5道最适合的菜品进行个性化推荐。
                        输出要求：1. 推荐菜品列表（含菜名、价格、推荐理由）2. 整体推荐策略说明""")
                .toolkit(dishToolkit)
                .memory(new InMemoryMemory());
        if (studioMessageHook != null) {
            dishBuilder.hook(studioMessageHook);
        }

        AgentScopeAgent dishAgent = AgentScopeAgent.fromBuilder(dishBuilder)
                .name("菜品推荐引擎")
                .description("根据用户画像推荐菜品")
                .instruction("以下是用户画像分析结果:\n{user_profile}\n\n请为该用户推荐菜品")
                .includeContents(false)
                .outputKey("recommendation")
                .build();

        // Step 3: 推荐结果落库
        Toolkit saveToolkit = new Toolkit();
        saveToolkit.registerTool(saveRecommendationTool);

        ReActAgent.Builder saveBuilder = ReActAgent.builder()
                .name("推荐落库助手")
                .model(pipelineModel)
                .sysPrompt("""
                        你是一个数据持久化助手。接收上游的菜品推荐结果，
                        使用 save_recommendation 工具将推荐结果保存到数据库中。
                        提取推荐的菜品列表和推荐理由，调用保存工具完成落库。保存完成后输出保存状态。""")
                .toolkit(saveToolkit)
                .memory(new InMemoryMemory());
        if (studioMessageHook != null) {
            saveBuilder.hook(studioMessageHook);
        }

        AgentScopeAgent saveAgent = AgentScopeAgent.fromBuilder(saveBuilder)
                .name("推荐落库助手")
                .description("将推荐结果保存到数据库")
                .instruction("用户ID: {input}\n以下是推荐结果:\n{recommendation}\n\n请将推荐结果保存到数据库")
                .includeContents(false)
                .outputKey("save_result")
                .build();

        return SequentialAgent.builder()
                .name("菜品推荐编排")
                .description("固定流程：用户画像分析 → 菜品匹配推荐 → 推荐结果落库")
                .subAgents(List.of(profileAgent, dishAgent, saveAgent))
                .build();
    }
}
