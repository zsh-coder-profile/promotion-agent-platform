package com.example.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ProductPerformanceTool {

    @Tool(name = "query_product_performance",
          description = "根据产品系列名称和指定周范围查询该系列产品的销售表现数据。" +
                        "可以查询指定周的销量、销售额、订单数、客单价、退货率等指标。" +
                        "周范围用相对值表示：0=本周，1=上周，2=上上周，以此类推。")
    public String queryProductPerformance(
            @ToolParam(name = "product_series", description = "产品系列名称，如草莓系、芒果系、抹茶系", required = true) String productSeries,
            @ToolParam(name = "week_offset", description = "周偏移量，0=本周，1=上周，2=上上周", required = true) int weekOffset) {

        System.out.println(">>> 正在查询产品表现 [系列=" + productSeries + ", 周偏移=" + weekOffset + "] ...");

        if ("草莓系".equals(productSeries) || "草莓".equals(productSeries)) {
            if (weekOffset == 1) {
                return """
                        {
                          "series": "草莓系",
                          "period": "上周 (2026-04-07 ~ 2026-04-13)",
                          "totalSales": 186500,
                          "orderCount": 2480,
                          "avgOrderAmount": 75.2,
                          "topProducts": [
                            {"name": "草莓奶昔", "sales": 62000, "orders": 820},
                            {"name": "草莓蛋糕", "sales": 55800, "orders": 680},
                            {"name": "草莓冰淇淋", "sales": 38200, "orders": 520},
                            {"name": "草莓果茶", "sales": 30500, "orders": 460}
                          ],
                          "returnRate": 0.018,
                          "newCustomerRate": 0.22,
                          "repeatPurchaseRate": 0.38,
                          "channelBreakdown": {
                            "线上外卖": 0.45,
                            "小程序": 0.30,
                            "到店": 0.25
                          }
                        }
                        """;
            } else if (weekOffset == 2) {
                return """
                        {
                          "series": "草莓系",
                          "period": "上上周 (2026-03-31 ~ 2026-04-06)",
                          "totalSales": 152300,
                          "orderCount": 2050,
                          "avgOrderAmount": 74.3,
                          "topProducts": [
                            {"name": "草莓奶昔", "sales": 51000, "orders": 680},
                            {"name": "草莓蛋糕", "sales": 45200, "orders": 560},
                            {"name": "草莓冰淇淋", "sales": 32100, "orders": 440},
                            {"name": "草莓果茶", "sales": 24000, "orders": 370}
                          ],
                          "returnRate": 0.022,
                          "newCustomerRate": 0.18,
                          "repeatPurchaseRate": 0.34,
                          "channelBreakdown": {
                            "线上外卖": 0.42,
                            "小程序": 0.28,
                            "到店": 0.30
                          }
                        }
                        """;
            }
        }

        return """
                {
                  "series": "%s",
                  "period": "周偏移=%d",
                  "totalSales": 0,
                  "orderCount": 0,
                  "message": "暂无该产品系列在指定时间范围的数据"
                }
                """.formatted(productSeries, weekOffset);
    }
}
