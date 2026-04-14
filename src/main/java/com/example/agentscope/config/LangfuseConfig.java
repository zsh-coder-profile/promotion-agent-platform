package com.example.agentscope.config;

import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

/**
 * Langfuse 可观测性配置
 * 通过 OpenTelemetry OTLP 协议将 Agent 的 trace 数据发送到 Langfuse
 */
@Configuration
public class LangfuseConfig {

    @Value("${langfuse.public-key:}")
    private String publicKey;

    @Value("${langfuse.secret-key:}")
    private String secretKey;

    @Value("${langfuse.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${langfuse.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void initLangfuseTracer() {
        if (!enabled || publicKey.isEmpty() || secretKey.isEmpty()) {
            System.out.println("Langfuse 追踪未启用（缺少 key 或已禁用）");
            return;
        }

        try {
            // Langfuse 要求 Basic Auth: Base64(publicKey:secretKey)
            String encoded = Base64.getEncoder()
                    .encodeToString((publicKey + ":" + secretKey).getBytes());

            // OTLP endpoint = baseUrl + /api/public/otel/v1/traces
            String endpoint = baseUrl.replaceAll("/+$", "") + "/api/public/otel/v1/traces";

            TracerRegistry.register(
                    TelemetryTracer.builder()
                            .endpoint(endpoint)
                            .addHeader("Authorization", "Basic " + encoded)
                            .addHeader("x-langfuse-ingestion-version", "4")
                            .build()
            );

            System.out.println("Langfuse 追踪已启用，endpoint: " + endpoint);
        } catch (Exception e) {
            System.err.println("Langfuse 追踪初始化失败: " + e.getMessage());
        }
    }
}
