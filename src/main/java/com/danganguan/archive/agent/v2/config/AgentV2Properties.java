package com.danganguan.archive.agent.v2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "archive.agent-v2")
@Getter
@Setter
public class AgentV2Properties {
    private boolean llmEnabled;

}
