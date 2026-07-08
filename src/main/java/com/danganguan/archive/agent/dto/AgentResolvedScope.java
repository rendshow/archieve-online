package com.danganguan.archive.agent.dto;

import com.danganguan.archive.agent.enums.AgentScopeType;

public record AgentResolvedScope(
        AgentScopeType scopeType,
        Long hallId,
        String folderPath,
        Long documentId,
        Long taskId,
        String source,
        String reason
) {
}
