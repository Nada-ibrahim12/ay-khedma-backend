package com.aykhedma.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;


@Configuration
@ConditionalOnProperty(name = "ai.gemini.use-spring-ai", havingValue = "true")
@EnableConfigurationProperties(SpringAiProperties.class)
@Slf4j
public class SpringAiConfig {
    public SpringAiConfig(SpringAiProperties properties) {
        if (StringUtils.hasText(properties.getProjectId())) {
            log.info("Spring AI enabled with Vertex AI project-id: {}", properties.getProjectId());
        } else {
            log.warn("Spring AI is enabled (ai.gemini.use-spring-ai=true) but VERTEX_PROJECT_ID is not set. " +
                    "Please provide VERTEX_PROJECT_ID environment variable or set AI_USE_SPRING_AI=false to use fallback Gemini client.");
        }
    }
}
