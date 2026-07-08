package com.danganguan.archive.agent.dto;

import com.danganguan.archive.agent.enums.AgentIntent;

import java.util.List;

public record AgentChatResponse(
        Long sessionId,
        AgentIntent intent,
        AgentResolvedScope scope,
        String answer,
        List<AgentDocumentReference> references
) {
}
