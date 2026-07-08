package com.danganguan.archive.agent.dto;

public record AgentChatRequest(
        Long sessionId,
        String message,
        AgentClientContext clientContext
) {
}
