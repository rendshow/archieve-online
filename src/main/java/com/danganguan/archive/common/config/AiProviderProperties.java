package com.danganguan.archive.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.ai")
public class AiProviderProperties {
    private String provider = "local";
    private OpenAiCompatible openaiCompatible = new OpenAiCompatible();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OpenAiCompatible getOpenaiCompatible() {
        return openaiCompatible;
    }

    public void setOpenaiCompatible(OpenAiCompatible openaiCompatible) {
        this.openaiCompatible = openaiCompatible;
    }

    public static class OpenAiCompatible {
        private String baseUrl = "https://api.openai.com/v1/chat/completions";
        private String apiKey;
        private String model;
        private int timeoutSeconds = 60;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
