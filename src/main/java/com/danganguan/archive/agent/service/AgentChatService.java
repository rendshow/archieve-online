package com.danganguan.archive.agent.service;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.dto.AgentChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentChatService {
    AgentChatResponse chat(AgentChatRequest request);

    SseEmitter stream(AgentChatRequest request);
}
