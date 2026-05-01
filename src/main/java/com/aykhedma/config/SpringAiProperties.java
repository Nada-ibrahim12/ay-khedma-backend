package com.aykhedma.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "spring.ai.vertex.ai.gemini")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpringAiProperties {
    private String projectId;
    private String location = "us-central1";
}
