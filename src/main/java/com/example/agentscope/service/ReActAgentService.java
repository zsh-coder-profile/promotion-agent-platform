package com.example.agentscope.service;

import com.example.agentscope.tool.ProductPerformanceTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.studio.StudioUserAgent;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReActAgentService {

    @Value("${agentscope.model.openai.api-key}")
    private String apiKey;

    @Value("${agentscope.model.openai.model-name}")
    private String modelName;

    @Value("${agentscope.model.openai.base-url}")
    private String baseUrl;

    @Value("${agentscope.studio.enabled:true}")
    private boolean studioEnabled;

    private final ProductPerformanceTool productPerformanceTool;

    @Autowired(required = false)
    private StudioMessageHook studioMessageHook;

    public ReActAgentService(ProductPerformanceTool productPerformanceTool) {
        this.productPerformanceTool = productPerformanceTool;
    }

    @PostConstruct
    public void startInteractiveAgent() {
        if (!studioEnabled || StudioManager.getClient() == null) {
            System.out.println("Studio 未启用或连接失败，交互式 Agent 不启动");
            return;
        }

        // 在独立线程中运行对话循环，避免阻塞 Spring 启动
        Thread agentThread = new Thread(this::runConversationLoop, "react-agent-thread");
        agentThread.setDaemon(true);
        agentThread.start();
    }

    private void runConversationLoop() {
        try {
            // 1. 注册工具
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(productPerformanceTool);

            // 2. 构建模型
            OpenAIChatModel model = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .baseUrl(baseUrl)
                    .build();

            // 3. 构建 ReAct 智能体
            ReActAgent.Builder agentBuilder = ReActAgent.builder()
                    .name("Friday")
                    .sysPrompt("""
                            你是 Friday，一个专业的运营数据分析助手。你的职责是帮助运营人员查询和分析产品销售数据。

                            你的能力：
                            1. 查询指定产品系列在不同时间段的销售表现数据
                            2. 对不同周期的数据进行对比分析，找出变化趋势
                            3. 给出数据驱动的运营建议

                            工作方式：
                            - 当用户问到某个产品系列在不同时间段的表现时，你需要分别查询每个时间段的数据
                            - 例如用户问"上周和上上周的对比"，你应该调用两次 query_product_performance 工具，分别传入 week_offset=1 和 week_offset=2
                            - 拿到所有数据后，进行全面的对比分析

                            分析输出要求：
                            1. 核心指标对比：销售额、订单量、客单价的环比变化
                            2. 单品表现：各产品的增长/下降排名
                            3. 渠道变化：各渠道占比的变动
                            4. 关键洞察：找出显著变化并分析可能原因
                            5. 运营建议：基于数据给出 2-3 条可执行建议

                            回答语言：简体中文
                            """)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(10);

            if (studioMessageHook != null) {
                agentBuilder.hook(studioMessageHook);
            }
            ReActAgent agent = agentBuilder.build();

            // 4. 构建 StudioUserAgent 接收 Web UI 输入
            StudioUserAgent user = StudioUserAgent.builder()
                    .name("运营人员")
                    .studioClient(StudioManager.getClient())
                    .webSocketClient(StudioManager.getWebSocketClient())
                    .build();

            System.out.println("=== Friday 运营分析助手已启动 ===");
            System.out.println("请打开 Studio Web UI 进行对话");

            // 5. 对话循环
            Msg msg = null;
            while (true) {
                msg = user.call(msg).block();
                if (msg == null || "exit".equalsIgnoreCase(msg.getTextContent())) {
                    System.out.println("对话结束");
                    break;
                }
                System.out.println("[用户] " + msg.getTextContent());

                msg = agent.call(msg).block();
                if (msg != null) {
                    System.out.println("[Friday] " + msg.getTextContent());
                }
            }
        } catch (Exception e) {
            System.err.println("Agent 对话循环异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
