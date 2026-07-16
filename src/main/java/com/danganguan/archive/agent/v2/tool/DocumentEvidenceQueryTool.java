package com.danganguan.archive.agent.v2.tool;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;

import java.util.List;

public interface DocumentEvidenceQueryTool {
    QueryResult query(String message, AgentResolvedScope scope);

    record QueryResult(String answer, List<ArchiveFactEvidence> evidence) {
    }
}
