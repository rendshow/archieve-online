package com.danganguan.archive.agent.v2.service.impl;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.agent.v2.service.AgentTaskPlanner;
import com.danganguan.archive.agent.v2.service.AgentV2ExecutionService;
import com.danganguan.archive.agent.v2.tool.DocumentEvidenceQueryTool;
import com.danganguan.archive.agent.v2.tool.ArchiveLocateTool;
import com.danganguan.archive.agent.v2.tool.ScopeAggregateTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentV2ExecutionServiceImpl implements AgentV2ExecutionService {
    private final AgentTaskPlanner agentTaskPlanner;
    private final DocumentEvidenceQueryTool documentEvidenceQueryTool;
    private final ArchiveLocateTool archiveLocateTool;
    private final ScopeAggregateTool scopeAggregateTool;

    @Override
    public AgentToolExecutionResult execute(AgentChatRequest request) {
        AgentChatRequest safeRequest = request == null ? new AgentChatRequest(null, null, null) : request;
        AgentTaskSpec task = agentTaskPlanner.plan(safeRequest.message(), safeRequest.clientContext());
        if (task.intent() == AgentTaskIntent.ANSWER_FROM_DOCUMENTS) {
            DocumentEvidenceQueryTool.QueryResult result = documentEvidenceQueryTool.query(safeRequest.message(), task.scope());
            return new AgentToolExecutionResult(task, result.evidence().isEmpty() ? "INSUFFICIENT_EVIDENCE" : "COMPLETED",
                    result.answer(), List.of(), result.evidence());
        }
        if (task.intent() == AgentTaskIntent.LOCATE_DOCUMENT) {
            ArchiveLocateTool.LocateResult result = archiveLocateTool.locate(safeRequest.message(), task.scope());
            return new AgentToolExecutionResult(task, result.documents().isEmpty() ? "NO_MATCH" : "COMPLETED",
                    result.answer(), result.documents(), result.evidence());
        }
        if (task.intent() == AgentTaskIntent.SUMMARIZE_SCOPE) {
            ScopeAggregateTool.AggregateResult result = scopeAggregateTool.aggregate(safeRequest.message(), task.scope());
            return new AgentToolExecutionResult(task, "COMPLETED", result.answer(), result.documents(), result.evidence());
        }
        if (task.intent() == AgentTaskIntent.CLARIFY || task.intent() == AgentTaskIntent.OUT_OF_SCOPE) {
            return new AgentToolExecutionResult(task, task.intent().name(), task.clarification(), List.of(), List.of());
        }
        return new AgentToolExecutionResult(task, "NOT_IMPLEMENTED",
                "该任务已被识别为“%s”，但对应工具尚未接入执行链路，因此不会生成推测性答案。".formatted(task.intent()), List.of(), List.of());
    }
}
