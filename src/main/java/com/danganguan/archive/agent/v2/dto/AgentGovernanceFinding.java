package com.danganguan.archive.agent.v2.dto;

import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;

import java.util.List;

public record AgentGovernanceFinding(
        String type,
        String level,
        String message,
        List<AgentDocumentReference> documents,
        List<ArchiveFactEvidence> evidence
) {
}
