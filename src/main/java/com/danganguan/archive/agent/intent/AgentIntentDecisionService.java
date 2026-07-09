package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.dto.AgentClientContext;

public interface AgentIntentDecisionService {
    AgentIntentDecision decide(String message, AgentClientContext context);
}
