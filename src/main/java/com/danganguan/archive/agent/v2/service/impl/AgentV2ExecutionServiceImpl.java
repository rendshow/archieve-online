package com.danganguan.archive.agent.v2.service.impl;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.agent.v2.service.AgentTaskPlanner;
import com.danganguan.archive.agent.v2.service.AgentV2ExecutionService;
import com.danganguan.archive.agent.v2.service.AgentV2AnswerComposer;
import com.danganguan.archive.agent.v2.tool.DocumentEvidenceQueryTool;
import com.danganguan.archive.agent.v2.tool.ArchiveLocateTool;
import com.danganguan.archive.agent.v2.tool.ScopeAggregateTool;
import com.danganguan.archive.agent.v2.tool.GovernanceInspectTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AgentV2ExecutionServiceImpl implements AgentV2ExecutionService {
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;
    private final AgentTaskPlanner agentTaskPlanner;
    private final DocumentEvidenceQueryTool documentEvidenceQueryTool;
    private final ArchiveLocateTool archiveLocateTool;
    private final ScopeAggregateTool scopeAggregateTool;
    private final GovernanceInspectTool governanceInspectTool;
    private final AgentV2AnswerComposer agentV2AnswerComposer;

    @Override
    public AgentToolExecutionResult execute(AgentChatRequest request) {
        AgentChatRequest safeRequest = request == null ? new AgentChatRequest(null, null, null) : request;
        AgentTaskSpec task = agentTaskPlanner.plan(safeRequest.message(), safeRequest.clientContext());
        if (task.intent() == AgentTaskIntent.ANSWER_FROM_DOCUMENTS) {
            DocumentEvidenceQueryTool.QueryResult result = documentEvidenceQueryTool.query(safeRequest.message(), task.scope());
            return compose(safeRequest.message(), new AgentToolExecutionResult(task,
                    result.evidence().isEmpty() ? "INSUFFICIENT_EVIDENCE" : "COMPLETED", "RULE",
                    result.answer(), List.of(), result.evidence(), List.of()));
        }
        if (task.intent() == AgentTaskIntent.LOCATE_DOCUMENT) {
            ArchiveLocateTool.LocateResult result = archiveLocateTool.locate(safeRequest.message(), task.scope());
            return compose(safeRequest.message(), new AgentToolExecutionResult(task,
                    result.documents().isEmpty() ? "NO_MATCH" : "COMPLETED", "RULE",
                    result.answer(), result.documents(), result.evidence(), List.of()));
        }
        if (task.intent() == AgentTaskIntent.SUMMARIZE_SCOPE) {
            ScopeAggregateTool.AggregateResult result = scopeAggregateTool.aggregate(safeRequest.message(), task.scope());
            return compose(safeRequest.message(), new AgentToolExecutionResult(task, "COMPLETED", "RULE",
                    result.answer(), result.documents(), result.evidence(), List.of()));
        }
        if (task.intent() == AgentTaskIntent.AUDIT_ARCHIVE) {
            GovernanceInspectTool.InspectResult result = governanceInspectTool.inspect(task.scope());
            return compose(safeRequest.message(), new AgentToolExecutionResult(task, "COMPLETED", "RULE",
                    result.answer(), result.documents(), result.evidence(), result.findings()));
        }
        if (task.intent() == AgentTaskIntent.CLARIFY || task.intent() == AgentTaskIntent.OUT_OF_SCOPE) {
            return new AgentToolExecutionResult(task, task.intent().name(), "RULE", task.clarification(), List.of(), List.of(), List.of());
        }
        return new AgentToolExecutionResult(task, "NOT_IMPLEMENTED", "RULE",
                "该任务已被识别为“%s”，但对应工具尚未接入执行链路，因此不会生成推测性答案。".formatted(task.intent()), List.of(), List.of(), List.of());
    }

    @Override
    public SseEmitter stream(AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                AgentToolExecutionResult result = execute(request);
                sendEvent(emitter, "meta", Map.of(
                        "intent", result.task().intent(),
                        "toolName", result.task().toolName(),
                        "scope", result.task().scope(),
                        "status", result.status(),
                        "answerSource", result.answerSource()
                ));
                emitBySegment(result.answer(), segment -> sendEvent(emitter, "delta", segment));
                sendEvent(emitter, "done", result);
                emitter.complete();
            } catch (Exception ex) {
                try {
                    sendEvent(emitter, "error", Map.of("message", "Agent V2 执行失败：" + safeMessage(ex)));
                } catch (IOException ignored) {
                    // Client may have closed the stream.
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private AgentToolExecutionResult compose(String userMessage, AgentToolExecutionResult rawResult) {
        AgentV2AnswerComposer.ComposeResult composed = agentV2AnswerComposer.compose(userMessage, rawResult);
        return new AgentToolExecutionResult(rawResult.task(), rawResult.status(), composed.source(), composed.answer(),
                rawResult.documents(), rawResult.evidence(), rawResult.findings());
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void emitBySegment(String answer, ThrowingConsumer<String> consumer) throws IOException {
        if (answer == null || answer.isBlank()) {
            return;
        }
        StringBuilder segment = new StringBuilder();
        for (int index = 0; index < answer.length(); index++) {
            char character = answer.charAt(index);
            segment.append(character);
            if ("。；！？\n".indexOf(character) >= 0 || segment.length() >= 30) {
                consumer.accept(segment.toString());
                segment.setLength(0);
            }
        }
        if (!segment.isEmpty()) {
            consumer.accept(segment.toString());
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }
}
