package com.danganguan.archive.agent.v2.service;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;

public interface AgentV2ExecutionService {
    AgentToolExecutionResult execute(AgentChatRequest request);
}
