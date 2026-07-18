package com.danganguan.archive.agent.v2.dto;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.v2.enums.AgentEvidenceRequirement;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.agent.v2.enums.AgentTaskOperation;

import java.util.List;

public record AgentTaskSpec(
        AgentTaskIntent intent,
        String toolName,
        AgentResolvedScope scope,
        List<String> requestedFields,
        AgentEvidenceRequirement evidenceRequirement,
        boolean requiresExistingIndex,
        String decisionReason,
        String clarification,
        List<AgentTaskOperation> operations
) {
    public AgentTaskSpec(AgentTaskIntent intent, String toolName, AgentResolvedScope scope,
                         List<String> requestedFields, AgentEvidenceRequirement evidenceRequirement,
                         boolean requiresExistingIndex, String decisionReason, String clarification) {
        this(intent, toolName, scope, requestedFields, evidenceRequirement, requiresExistingIndex,
                decisionReason, clarification, defaultOperations(intent));
    }

    private static List<AgentTaskOperation> defaultOperations(AgentTaskIntent intent) {
        return switch (intent) {
            case LOCATE_DOCUMENT -> List.of(AgentTaskOperation.LOCATE_DOCUMENTS, AgentTaskOperation.SEARCH_PAGE_TEXT);
            case ANSWER_FROM_DOCUMENTS -> List.of(AgentTaskOperation.LOCATE_DOCUMENTS,
                    AgentTaskOperation.READ_PAGE_FACTS, AgentTaskOperation.COMPOSE_EVIDENCED_ANSWER);
            case SUMMARIZE_SCOPE -> List.of(AgentTaskOperation.AGGREGATE_SCOPE, AgentTaskOperation.COMPOSE_EVIDENCED_ANSWER);
            case AUDIT_ARCHIVE -> List.of(AgentTaskOperation.INSPECT_GOVERNANCE, AgentTaskOperation.COMPOSE_EVIDENCED_ANSWER);
            case CLARIFY, OUT_OF_SCOPE -> List.of(AgentTaskOperation.REQUEST_CLARIFICATION);
        };
    }
}
