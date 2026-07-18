package com.danganguan.archive.common.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "archive.boundary")
@Getter
@Setter
public class BoundaryDetectionProperties {
    private String provider = "rule";
    private BigDecimal ruleConfidenceThreshold = new BigDecimal("0.80");
    private OpenAiCompatible openaiCompatible = new OpenAiCompatible();




    @Getter
    @Setter
    public static class OpenAiCompatible {
        private String baseUrl = "https://api.openai.com/v1/chat/completions";
        private String apiKey;
        private String model;
        private int timeoutSeconds = 60;




    }
}
