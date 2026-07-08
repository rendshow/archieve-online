package com.danganguan.archive.agent.service;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.dto.AgentChatResponse;

public interface AgentChatService {
    AgentChatResponse chat(AgentChatRequest request);
}
