package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DishQueryTool {

    @Tool(name = "query_dishes", description = "根据菜品分类和标签从菜品标签库中查询候选菜品列表")
    public String queryDishes(
            @ToolParam(name = "category", description = "菜品分类，如川菜、粤菜", required = true) String category,
            @ToolParam(name = "tags", description = "筛选标签，如辣、清淡", required = false) String tags) {
        System.out.println(">>> 正在查询菜品 [分类=" + category + ", 标签=" + tags + "] ...");
        return """
                [
                  {"dishId": "D001", "name": "麻婆豆腐", "price": 28, "tags": ["川菜","辣","豆腐"], "rating": 4.8},
                  {"dishId": "D002", "name": "水煮鱼片", "price": 58, "tags": ["川菜","辣","鱼"], "rating": 4.9},
                  {"dishId": "D003", "name": "宫保鸡丁", "price": 35, "tags": ["川菜","微辣","鸡肉"], "rating": 4.7},
                  {"dishId": "D004", "name": "回锅肉", "price": 38, "tags": ["川菜","辣","猪肉"], "rating": 4.6},
                  {"dishId": "D005", "name": "酸菜鱼", "price": 52, "tags": ["川菜","酸辣","鱼"], "rating": 4.8},
                  {"dishId": "D006", "name": "干煸四季豆", "price": 22, "tags": ["川菜","微辣","素菜"], "rating": 4.5}
                ]
                """;
    }
}
