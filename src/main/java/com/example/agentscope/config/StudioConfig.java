package com.example.agentscope.config;

import io.agentscope.core.studio.StudioClient;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 Studio 可观测性配置
 * 应用启动时初始化 StudioManager，关闭时释放资源
 */
@Configuration
public class StudioConfig {

    @Value("${agentscope.studio.url:http://localhost:3000}")
    private String studioUrl;

    @Value("${agentscope.studio.project:agent_scope_engine}")
    private String studioProject;

    @Value("${agentscope.studio.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void initStudio() {
        if (!enabled) {
            System.out.println("Studio 可观测性已禁用");
            return;
        }
        try {
            StudioManager.init()
                    .studioUrl(studioUrl)
                    .project(studioProject)
                    .runName("run_" + System.currentTimeMillis())
                    .initialize()
                    .block();
            System.out.println("Studio 全局连接已建立: " + studioUrl);
        } catch (Exception e) {
            System.err.println("Studio 连接失败，可观测性功能不可用: " + e.getMessage());
        }
    }

    @Bean
    public StudioMessageHook studioMessageHook() {
        StudioClient client = StudioManager.getClient();
        if (client != null) {
            return new StudioMessageHook(client);
        }
        System.err.println("StudioClient 为 null，StudioMessageHook 将不可用");
        return null;
    }

    @PreDestroy
    public void shutdownStudio() {
        try {
            StudioManager.shutdown();
            System.out.println("Studio 连接已关闭");
        } catch (Exception e) {
            System.err.println("Studio 关闭异常: " + e.getMessage());
        }
    }
}
