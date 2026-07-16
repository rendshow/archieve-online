package com.danganguan.archive.agent.v2.service;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentV2ExecutionService {
    AgentToolExecutionResult execute(AgentChatRequest request);

    SseEmitter stream(AgentChatRequest request);
}
