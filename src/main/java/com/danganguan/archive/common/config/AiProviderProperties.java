package com.danganguan.archive.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "archive.ai")
@Getter
@Setter
public class AiProviderProperties {
    private String provider = "local";
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
