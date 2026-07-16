package com.danganguan.archive.agent.v2.dto;

import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;

import java.util.List;

public record AgentToolExecutionResult(
        AgentTaskSpec task,
        String status,
        String answerSource,
        String answer,
        List<AgentDocumentReference> documents,
        List<ArchiveFactEvidence> evidence,
        List<AgentGovernanceFinding> findings
) {
}
