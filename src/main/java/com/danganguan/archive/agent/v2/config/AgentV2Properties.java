package com.danganguan.archive.agent.v2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.agent-v2")
public class AgentV2Properties {
    private boolean llmEnabled;

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }
}
