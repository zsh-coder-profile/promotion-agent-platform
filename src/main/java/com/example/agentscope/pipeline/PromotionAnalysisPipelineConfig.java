package com.example.agentscope.pipeline;

import com.alibaba.cloud.ai.agent.agentscope.AgentScopeAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.example.agentscope.tool.PromotionDataTool;
import com.example.agentscope.tool.SaveAnalysisResultTool;
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
 * 促销活动分析编排 Pipeline（SDK SequentialAgent）
 * 固定流程：活动数据采集 → ROI/效果分析 → 分析结果落库
 */
@Configuration
public class PromotionAnalysisPipelineConfig {

    @Autowired(required = false)
    private StudioMessageHook studioMessageHook;

    @Bean("promotionAnalysisPipeline")
    public SequentialAgent promotionAnalysisPipeline(OpenAIChatModel pipelineModel,
                                                      PromotionDataTool promotionDataTool,
                                                      SaveAnalysisResultTool saveAnalysisResultTool) {

        // Step 1: 活动数据采集
        Toolkit dataToolkit = new Toolkit();
        dataToolkit.registerTool(promotionDataTool);

        ReActAgent.Builder collectorBuilder = ReActAgent.builder()
                .name("活动数据采集员")
                .model(pipelineModel)
                .sysPrompt("""
                        你是一个促销活动数据采集专员。根据提供的活动ID，
                        使用 get_promotion_data 工具获取该活动的完整数据。
                        将获取到的原始数据整理输出。""")
                .toolkit(dataToolkit)
                .memory(new InMemoryMemory());
        if (studioMessageHook != null) {
            collectorBuilder.hook(studioMessageHook);
        }

        AgentScopeAgent collectorAgent = AgentScopeAgent.fromBuilder(collectorBuilder)
                .name("活动数据采集员")
                .description("根据活动ID获取促销活动数据")
                .instruction("请获取活动ID为 {input} 的促销活动数据")
                .includeContents(false)
                .outputKey("promotion_data")
                .build();

        // Step 2: 效果分析 (纯 LLM，无工具)
        ReActAgent.Builder analyzerBuilder = ReActAgent.builder()
                .name("活动效果分析师")
                .model(pipelineModel)
                .sysPrompt("""
                        你是一个资深的促销活动效果分析师。根据上游提供的活动数据，
                        进行深度分析并输出：
                        1. 活动整体评价：ROI 是否达标、预算使用效率
                        2. 关键指标分析：核销率、复购率、拉新效果、客单价变化
                        3. 时段分析：高峰时段表现
                        4. 热门菜品分析
                        5. 优化建议（3-5条可执行建议）
                        6. 效果预测
                        分析要数据驱动、结论明确、建议可落地。""")
                .memory(new InMemoryMemory());
        if (studioMessageHook != null) {
            analyzerBuilder.hook(studioMessageHook);
        }

        AgentScopeAgent analyzerAgent = AgentScopeAgent.fromBuilder(analyzerBuilder)
                .name("活动效果分析师")
                .description("对促销活动数据进行深度分析")
                .instruction("以下是活动原始数据:\n{promotion_data}\n\n请进行深度分析")
                .includeContents(false)
                .outputKey("analysis_result")
                .build();

        // Step 3: 分析结果落库
        Toolkit saveToolkit = new Toolkit();
        saveToolkit.registerTool(saveAnalysisResultTool);

        ReActAgent.Builder saverBuilder = ReActAgent.builder()
                .name("分析落库助手")
                .model(pipelineModel)
                .sysPrompt("""
                        你是一个数据持久化助手。接收上游的活动分析报告，
                        使用 save_analysis_result 工具将分析结果保存到数据库中。
                        提取分析报告的核心内容和优化建议，调用保存工具完成落库。""")
                .toolkit(saveToolkit)
                .memory(new InMemoryMemory());
        if (studioMessageHook != null) {
            saverBuilder.hook(studioMessageHook);
        }

        AgentScopeAgent saverAgent = AgentScopeAgent.fromBuilder(saverBuilder)
                .name("分析落库助手")
                .description("将分析结果保存到数据库")
                .instruction("活动ID: {input}\n以下是分析报告:\n{analysis_result}\n\n请将分析结果保存到数据库")
                .includeContents(false)
                .outputKey("save_result")
                .build();

        return SequentialAgent.builder()
                .name("促销活动分析编排")
                .description("固定流程：活动数据采集 → ROI/效果分析 → 分析结果落库")
                .subAgents(List.of(collectorAgent, analyzerAgent, saverAgent))
                .build();
    }
}
