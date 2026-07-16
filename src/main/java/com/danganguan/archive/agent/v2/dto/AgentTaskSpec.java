package com.danganguan.archive.agent.v2.dto;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.v2.enums.AgentEvidenceRequirement;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;

import java.util.List;

public record AgentTaskSpec(
        AgentTaskIntent intent,
        String toolName,
        AgentResolvedScope scope,
        List<String> requestedFields,
        AgentEvidenceRequirement evidenceRequirement,
        boolean requiresExistingIndex,
        String decisionReason,
        String clarification
) {
}
