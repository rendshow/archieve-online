package com.danganguan.archive.agent.v2.tool;

import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;

import java.util.List;

public interface ScopeAggregateTool {
    AggregateResult aggregate(String message, AgentResolvedScope scope);

    record AggregateResult(String answer, List<AgentDocumentReference> documents,
                           List<ArchiveFactEvidence> evidence) {
    }
}
