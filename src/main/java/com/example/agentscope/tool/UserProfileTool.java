package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class UserProfileTool {

    @Tool(name = "get_user_profile", description = "根据用户ID获取用户画像，包含口味偏好、历史订单摘要、RFM分层等信息")
    public String getUserProfile(
            @ToolParam(name = "user_id", description = "用户ID", required = true) String userId) {
        System.out.println(">>> 正在查询用户画像 [" + userId + "] ...");
        return """
                {
                  "userId": "%s",
                  "name": "张三",
                  "tastePref": ["偏辣", "川菜", "不吃香菜"],
                  "orderHistory": "近30天下单12次，平均客单价68元，常点:麻婆豆腐、水煮鱼、宫保鸡丁",
                  "rfmLevel": "高价值活跃用户",
                  "scene": "午餐工作餐为主",
                  "priceRange": "40-100元"
                }
                """.formatted(userId);
    }
}
