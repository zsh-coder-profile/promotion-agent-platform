package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WeatherTool {
    
    @Tool(name = "get_weather", description = "根据城市名获取当前天气情况")
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，如北京", required = true) String city) {
        // 模拟调用第三方天气API获取结果
        System.out.println(">>> 正在调用 WeatherTool 获取 [" + city + "] 的天气...");
        return city + " 今天晴，气温 25度。";
    }
}
