package com.example.agentscope.service;

import com.example.agentscope.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.studio.StudioMessageHook;
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

    private final WeatherTool weatherTool;

    @Autowired(required = false)
    private StudioMessageHook studioMessageHook;

    public ReActAgentService(WeatherTool weatherTool) {
        this.weatherTool = weatherTool;
    }

    @PostConstruct
    public void runReActDemo() {
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("API_KEY 不能为空");
            return;
        }

        // 1. 组装 Toolkit 工具集
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(weatherTool);

        // 2. 初始化 OpenAI (兼容格式) 模型
        OpenAIChatModel model = OpenAIChatModel.builder().apiKey(apiKey).modelName(modelName).baseUrl(baseUrl).build();

        // 3. 构建 ReAct 智能体（使用全局 StudioMessageHook）
        ReActAgent.Builder agentBuilder = ReActAgent.builder()
                .name("WeatherAssistant")
                .sysPrompt("你是一个天气查询助手。你可以使用提供的工具查询天气信息并回复用户。在回答用户问题时，使用由工具提供的数据。只回答和使用工具能获取的天气相关信息")
                .model(model)
                .toolkit(toolkit);
        if (studioMessageHook != null) {
            agentBuilder.hook(studioMessageHook);
        }
        ReActAgent agent = agentBuilder.build();

        // 4. 用户输入并触发 ReAct 对话循环
        String userQuery = "你能帮我查一下今天北京的天气怎么样吗？";
        System.out.println("User: " + userQuery);

        Msg userInput = Msg.builder().name("User").textContent(userQuery).build();

        // 执行调用 (ReAct 循环：思考 -> 动作 -> 工具执行 -> 响应)
        Msg response = agent.call(userInput).block();

        System.out.println(agent.getName() + ": " + response.getTextContent());
    }

}
