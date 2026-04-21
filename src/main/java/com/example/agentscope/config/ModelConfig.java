package com.example.agentscope.config;

import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pipeline 编排共享的 OpenAI 兼容模型 Bean
 */
@Configuration
public class ModelConfig {

    @Value("${agentscope.model.openai.api-key}")
    private String apiKey;

    @Value("${agentscope.model.openai.model-name}")
    private String modelName;

    @Value("${agentscope.model.openai.base-url}")
    private String baseUrl;

    @Bean
    public OpenAIChatModel pipelineModel() {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .build();
    }

}
