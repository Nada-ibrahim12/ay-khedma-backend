package com.aykhedma.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "ai.gemini.use-spring-ai", havingValue = "false", matchIfMissing = true)
@Slf4j
public class DisableSpringAiConfig {
    public DisableSpringAiConfig() {
        log.debug("Spring AI is disabled (ai.gemini.use-spring-ai=false). Using fallback Gemini WebClient.");
    }
}
