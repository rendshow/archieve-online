package com.danganguan.archive.agent.service.impl;

import com.danganguan.archive.agent.context.AgentContextResolver;
import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.dto.AgentChatResponse;
import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.entity.AgentMessage;
import com.danganguan.archive.agent.entity.AgentSession;
import com.danganguan.archive.agent.enums.AgentIntent;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.intent.AgentIntentDecision;
import com.danganguan.archive.agent.intent.AgentIntentDecisionService;
import com.danganguan.archive.agent.llm.AgentAnswerLlmService;
import com.danganguan.archive.agent.mapper.AgentMessageMapper;
import com.danganguan.archive.agent.mapper.AgentSessionMapper;
import com.danganguan.archive.agent.service.AgentChatService;
import com.danganguan.archive.agent.tool.AgentArchiveContentTool;
import com.danganguan.archive.agent.tool.AgentArchiveTool;
import com.danganguan.archive.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentIntentDecisionService intentDecisionService;
    private final AgentContextResolver contextResolver;
    private final AgentArchiveTool archiveTool;
    private final AgentArchiveContentTool contentTool;
    private final AgentAnswerLlmService answerLlmService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentChatResponse chat(AgentChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BizException("消息不能为空");
        }
        AgentSession session = getOrCreateSession(request);
        AgentIntentDecision decision = intentDecisionService.decide(request.message(), request.clientContext());
        AgentIntent intent = decision.intent();
        AgentResolvedScope scope = contextResolver.resolve(intent, request.clientContext());

        AgentChatResponse response;
        if (isOutOfPageScope(request.message(), scope)) {
            response = outOfScope(session.getId(), scope);
        } else if (needsScope(intent, scope)) {
            response = needScope(session.getId(), intent, scope);
        } else {
            response = switch (intent) {
                case SEARCH_ARCHIVE -> search(session.getId(), request.message(), scope);
                case SUMMARIZE_SCOPE -> summarize(session.getId(), request.message(), scope);
                case YEAR_DISTRIBUTION -> summarizeYears(session.getId(), scope);
                case CHECK_MISSING_MATERIALS -> checkMissingMaterials(session.getId(), request.message(), scope);
                case DISCUSS_ARCHIVE_INFO -> discussArchiveInfo(session.getId(), request.message(), scope, request.clientContext());
                case CAPABILITY_HELP -> capabilityHelp(session.getId(), scope);
                default -> unknown(session.getId(), scope);
            };
        }

        saveMessage(session.getId(), "USER", request.message(), intent, request.clientContext(), Map.of("scope", scope, "decision", decision));
        saveMessage(session.getId(), "ASSISTANT", response.answer(), response.intent(), request.clientContext(), response.scope());
        touchSession(session, request.message());
        return response;
    }

    @Override
    public SseEmitter stream(AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                AgentChatResponse response = chatStreamInternal(request, emitter);
                sendEvent(emitter, "done", response);
                emitter.complete();
            } catch (Exception ex) {
                try {
                    sendEvent(emitter, "error", "Agent 流式回答失败：" + ex.getMessage());
                } catch (IOException ignored) {
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private AgentChatResponse chatStreamInternal(AgentChatRequest request, SseEmitter emitter) throws IOException {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BizException("消息不能为空");
        }
        AgentSession session = getOrCreateSession(request);
        AgentIntentDecision decision = intentDecisionService.decide(request.message(), request.clientContext());
        AgentIntent intent = decision.intent();
        AgentResolvedScope scope = contextResolver.resolve(intent, request.clientContext());
        sendEvent(emitter, "meta", Map.of(
                "sessionId", session.getId(),
                "intent", intent.name(),
                "scope", scope,
                "decision", decision
        ));

        AgentChatResponse response;
        if (isOutOfPageScope(request.message(), scope)) {
            response = outOfScope(session.getId(), scope);
            sendEvent(emitter, "delta", response.answer());
        } else if (needsScope(intent, scope) || needsContentScope(intent, scope, request.clientContext())) {
            response = needScope(session.getId(), intent, scope);
            sendEvent(emitter, "delta", response.answer());
        } else {
            Consumer<String> chunkConsumer = chunk -> {
                try {
                    sendEvent(emitter, "delta", chunk);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            };
            response = switch (intent) {
                case SEARCH_ARCHIVE -> searchStream(session.getId(), request.message(), scope, chunkConsumer);
                case SUMMARIZE_SCOPE -> summarizeStream(session.getId(), request.message(), scope, chunkConsumer);
                case YEAR_DISTRIBUTION -> summarizeYearsStream(session.getId(), scope, chunkConsumer);
                case CHECK_MISSING_MATERIALS -> checkMissingMaterialsStream(session.getId(), request.message(), scope, chunkConsumer);
                case DISCUSS_ARCHIVE_INFO -> discussArchiveInfoStream(session.getId(), request.message(), scope, request.clientContext(), chunkConsumer);
                case CAPABILITY_HELP -> capabilityHelpStream(session.getId(), scope, chunkConsumer);
                default -> unknown(session.getId(), scope);
            };
            if (response.intent() == AgentIntent.UNKNOWN) {
                sendEvent(emitter, "delta", response.answer());
            }
        }

        saveMessage(session.getId(), "USER", request.message(), intent, request.clientContext(), Map.of("scope", scope, "decision", decision));
        saveMessage(session.getId(), "ASSISTANT", response.answer(), response.intent(), request.clientContext(), response.scope());
        touchSession(session, request.message());
        return response;
    }

    private AgentSession getOrCreateSession(AgentChatRequest request) {
        if (request.sessionId() != null) {
            AgentSession existing = agentSessionMapper.selectById(request.sessionId());
            if (existing != null) {
                return existing;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        AgentSession session = new AgentSession();
        session.setTitle(buildTitle(request.message()));
        session.setCreatedBy(null);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);
        return session;
    }

    private AgentChatResponse search(Long sessionId, String message, AgentResolvedScope scope) {
        AgentArchiveTool.SearchResult result = archiveTool.search(message, scope);
        String scopeText = scopeText(scope);
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText).append("检索");
        if (!result.keywords().isEmpty()) {
            answer.append("，使用关键词：").append(String.join("、", result.keywords()));
        }
        answer.append("。");
        if (result.references().isEmpty()) {
            answer.append("没有找到匹配的正式档案。");
        } else if (result.requiresMaterialEvidence() && !result.hasMaterialContentEvidence()) {
            answer.append("找到 ").append(result.total())
                    .append(" 条相关档案，但当前只命中了题名、目录或基础摘要，不能确认其属于")
                    .append(String.join("、", result.materialKeywords()))
                    .append("。相关档案包括：");
            appendReferences(answer, result.references(), 5);
        } else {
            answer.append("找到 ").append(result.total()).append(" 条候选档案，前几条包括：");
            appendReferences(answer, result.references(), 8);
        }
        String finalAnswer = answerLlmService.enhance(message, AgentIntent.SEARCH_ARCHIVE, scope,
                answer.toString(), result.references(), result);
        return new AgentChatResponse(sessionId, AgentIntent.SEARCH_ARCHIVE, scope, finalAnswer, result.references());
    }

    private AgentChatResponse searchStream(Long sessionId, String message, AgentResolvedScope scope, Consumer<String> chunkConsumer) {
        AgentArchiveTool.SearchResult result = archiveTool.search(message, scope);
        String draftAnswer = searchDraft(message, scope, result);
        String finalAnswer = answerLlmService.enhanceStream(message, AgentIntent.SEARCH_ARCHIVE, scope,
                draftAnswer, result.references(), result, chunkConsumer);
        return new AgentChatResponse(sessionId, AgentIntent.SEARCH_ARCHIVE, scope, finalAnswer, result.references());
    }

    private AgentChatResponse summarize(Long sessionId, String message, AgentResolvedScope scope) {
        AgentArchiveTool.ScopeSummary summary = archiveTool.summarize(scope);
        if (isCountOnlyQuestion(message)) {
            String answer = scopeText(scope) + "共有 " + summary.documentCount() + " 份正式档案。";
            return new AgentChatResponse(sessionId, AgentIntent.SUMMARIZE_SCOPE, scope, answer, List.of());
        }
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("汇总：共 ")
                .append(summary.documentCount()).append(" 份正式档案");
        if (summary.personCount() > 0) {
            answer.append("，可识别约 ").append(summary.personCount()).append(" 名学生");
        }
        answer.append("。其中成绩单 ").append(summary.transcriptCount())
                .append(" 份，学籍材料 ").append(summary.studentStatusCount())
                .append(" 份，学位材料 ").append(summary.degreeCount()).append(" 份。");
        if (!summary.materialCounts().isEmpty()) {
            answer.append("材料分布：");
            for (Map.Entry<String, Integer> entry : summary.materialCounts().entrySet()) {
                answer.append(entry.getKey()).append(" ").append(entry.getValue()).append(" 份；");
            }
        }
        if (!summary.sampleReferences().isEmpty()) {
            answer.append("样例档案：");
            appendReferences(answer, summary.sampleReferences(), 5);
        }
        String finalAnswer = answerLlmService.enhance(message, AgentIntent.SUMMARIZE_SCOPE, scope,
                answer.toString(), summary.sampleReferences(), summary);
        return new AgentChatResponse(sessionId, AgentIntent.SUMMARIZE_SCOPE, scope, finalAnswer, summary.sampleReferences());
    }

    private AgentChatResponse summarizeStream(Long sessionId, String message, AgentResolvedScope scope, Consumer<String> chunkConsumer) {
        AgentArchiveTool.ScopeSummary summary = archiveTool.summarize(scope);
        if (isCountOnlyQuestion(message)) {
            String answer = scopeText(scope) + "共有 " + summary.documentCount() + " 份正式档案。";
            chunkConsumer.accept(answer);
            return new AgentChatResponse(sessionId, AgentIntent.SUMMARIZE_SCOPE, scope, answer, List.of());
        }
        String draftAnswer = summarizeDraft(scope, summary);
        String finalAnswer = answerLlmService.enhanceStream(message, AgentIntent.SUMMARIZE_SCOPE, scope,
                draftAnswer, summary.sampleReferences(), summary, chunkConsumer);
        return new AgentChatResponse(sessionId, AgentIntent.SUMMARIZE_SCOPE, scope, finalAnswer, summary.sampleReferences());
    }

    private AgentChatResponse summarizeYears(Long sessionId, AgentResolvedScope scope) {
        AgentArchiveTool.YearDistribution distribution = archiveTool.summarizeYears(scope);
        String answer = yearDistributionAnswer(scope, distribution);
        return new AgentChatResponse(sessionId, AgentIntent.YEAR_DISTRIBUTION, scope, answer, distribution.sampleReferences());
    }

    private AgentChatResponse summarizeYearsStream(Long sessionId, AgentResolvedScope scope, Consumer<String> chunkConsumer) {
        AgentChatResponse response = summarizeYears(sessionId, scope);
        chunkConsumer.accept(response.answer());
        return response;
    }

    private AgentChatResponse checkMissingMaterials(Long sessionId, String message, AgentResolvedScope scope) {
        AgentArchiveTool.MissingMaterialResult result = archiveTool.checkMissingMaterials(scope);
        List<AgentDocumentReference> references = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("做了轻量缺件核验。");
        if (result.personCount() == 0) {
            answer.append("当前范围内还没有足够的学生姓名线索，无法判断缺件情况。");
            String finalAnswer = answerLlmService.enhance(message, AgentIntent.CHECK_MISSING_MATERIALS, scope,
                    answer.toString(), List.of(), result);
            return new AgentChatResponse(sessionId, AgentIntent.CHECK_MISSING_MATERIALS, scope, finalAnswer, List.of());
        }
        answer.append("可识别约 ").append(result.personCount()).append(" 名学生，其中 ")
                .append(result.missingPersonCount()).append(" 名存在疑似缺件风险。");
        if (!result.missingPeople().isEmpty()) {
            answer.append("前几项风险：");
            for (AgentArchiveTool.MissingPerson person : result.missingPeople().stream().limit(10).toList()) {
                answer.append(person.name()).append("疑似缺");
                List<String> missing = new ArrayList<>();
                if (person.missingTranscript()) {
                    missing.add("成绩单");
                }
                if (person.missingStudentStatus()) {
                    missing.add("学籍材料");
                }
                if (person.missingDegree()) {
                    missing.add("学位材料");
                }
                answer.append(String.join("、", missing)).append("；");
                references.addAll(person.references());
            }
        }
        answer.append("以上仅根据当前系统可见文件名、标签摘要和 OCR 文本判断，建议人工复核。");
        List<AgentDocumentReference> limitedReferences = references.stream().limit(20).toList();
        String finalAnswer = answerLlmService.enhance(message, AgentIntent.CHECK_MISSING_MATERIALS, scope,
                answer.toString(), limitedReferences, result);
        return new AgentChatResponse(sessionId, AgentIntent.CHECK_MISSING_MATERIALS, scope, finalAnswer, limitedReferences);
    }

    private AgentChatResponse checkMissingMaterialsStream(Long sessionId, String message, AgentResolvedScope scope, Consumer<String> chunkConsumer) {
        MissingDraft draft = missingDraft(scope);
        String finalAnswer = answerLlmService.enhanceStream(message, AgentIntent.CHECK_MISSING_MATERIALS, scope,
                draft.answer(), draft.references(), draft.result(), chunkConsumer);
        return new AgentChatResponse(sessionId, AgentIntent.CHECK_MISSING_MATERIALS, scope, finalAnswer, draft.references());
    }

    private AgentChatResponse discussArchiveInfo(Long sessionId, String message, AgentResolvedScope scope, AgentClientContext context) {
        if (needsContentScope(AgentIntent.DISCUSS_ARCHIVE_INFO, scope, context)) {
            return needScope(sessionId, AgentIntent.DISCUSS_ARCHIVE_INFO, scope);
        }
        AgentArchiveContentTool.DiscussResult result = contentTool.discuss(message, scope, context);
        String draftAnswer = discussDraft(message, scope, result);
        String finalAnswer = answerLlmService.enhance(message, AgentIntent.DISCUSS_ARCHIVE_INFO, scope,
                draftAnswer, result.references(), result);
        return new AgentChatResponse(sessionId, AgentIntent.DISCUSS_ARCHIVE_INFO, scope, finalAnswer, result.references());
    }

    private AgentChatResponse discussArchiveInfoStream(Long sessionId, String message, AgentResolvedScope scope,
                                                       AgentClientContext context, Consumer<String> chunkConsumer) {
        AgentArchiveContentTool.DiscussResult result = contentTool.discuss(message, scope, context);
        String draftAnswer = discussDraft(message, scope, result);
        String finalAnswer = answerLlmService.enhanceStream(message, AgentIntent.DISCUSS_ARCHIVE_INFO, scope,
                draftAnswer, result.references(), result, chunkConsumer);
        return new AgentChatResponse(sessionId, AgentIntent.DISCUSS_ARCHIVE_INFO, scope, finalAnswer, result.references());
    }

    private AgentChatResponse outOfScope(Long sessionId, AgentResolvedScope scope) {
        String answer = "当前对话范围限定在" + scopeText(scope)
                + "，不能扩展到页面范围之外查询。请回到全局档案页或对应上级目录后再提问。";
        return new AgentChatResponse(sessionId, AgentIntent.OUT_OF_SCOPE, scope, answer, List.of());
    }

    private AgentChatResponse needScope(Long sessionId, AgentIntent intent, AgentResolvedScope scope) {
        String answer = intent == AgentIntent.DISCUSS_ARCHIVE_INFO
                ? "这个问题需要明确的档案内容范围。请先进入某个档案详情页、选中要讨论的档案，或进入具体文件夹后再提问。"
                : "这个问题需要明确的目录或馆区范围。请先进入要汇总/核验的文件夹，或在全局档案页选择馆区后再提问。";
        return new AgentChatResponse(sessionId, AgentIntent.NEED_SCOPE, scope, answer, List.of());
    }

    private AgentChatResponse unknown(Long sessionId, AgentResolvedScope scope) {
        return new AgentChatResponse(sessionId, AgentIntent.UNKNOWN, scope, capabilityText(), List.of());
    }

    private AgentChatResponse capabilityHelp(Long sessionId, AgentResolvedScope scope) {
        return new AgentChatResponse(sessionId, AgentIntent.CAPABILITY_HELP, scope, capabilityText(), List.of());
    }

    private AgentChatResponse capabilityHelpStream(Long sessionId, AgentResolvedScope scope, Consumer<String> chunkConsumer) {
        String answer = capabilityText();
        chunkConsumer.accept(answer);
        return new AgentChatResponse(sessionId, AgentIntent.CAPABILITY_HELP, scope, answer, List.of());
    }

    private boolean isOutOfPageScope(String message, AgentResolvedScope scope) {
        if (scope.scopeType() != AgentScopeType.FOLDER && scope.scopeType() != AgentScopeType.DOCUMENT && scope.scopeType() != AgentScopeType.TASK) {
            return false;
        }
        String text = message == null ? "" : message;
        return text.contains("全校") || text.contains("全馆") || text.contains("全部")
                || text.contains("所有馆") || text.contains("整个学校") || text.contains("所有档案");
    }

    private boolean needsScope(AgentIntent intent, AgentResolvedScope scope) {
        return (intent == AgentIntent.SUMMARIZE_SCOPE
                || intent == AgentIntent.YEAR_DISTRIBUTION
                || intent == AgentIntent.CHECK_MISSING_MATERIALS)
                && scope.scopeType() == AgentScopeType.GLOBAL
                && scope.hallId() == null;
    }

    private boolean needsContentScope(AgentIntent intent, AgentResolvedScope scope, AgentClientContext context) {
        return intent == AgentIntent.DISCUSS_ARCHIVE_INFO
                && scope.scopeType() == AgentScopeType.GLOBAL
                && !contentTool.hasExplicitContentScope(scope, context);
    }

    private String searchDraft(String message, AgentResolvedScope scope, AgentArchiveTool.SearchResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("检索");
        if (!result.keywords().isEmpty()) {
            answer.append("，使用关键词：").append(String.join("、", result.keywords()));
        }
        answer.append("。");
        if (result.references().isEmpty()) {
            answer.append("没有找到匹配的正式档案。");
        } else if (result.requiresMaterialEvidence() && !result.hasMaterialContentEvidence()) {
            answer.append("找到 ").append(result.total())
                    .append(" 条相关档案，但当前只命中了题名、目录或基础摘要，不能确认其属于")
                    .append(String.join("、", result.materialKeywords()))
                    .append("。相关档案包括：");
            appendReferences(answer, result.references(), 5);
        } else {
            answer.append("找到 ").append(result.total()).append(" 条候选档案，前几条包括：");
            appendReferences(answer, result.references(), 8);
        }
        return answer.toString();
    }

    private String summarizeDraft(AgentResolvedScope scope, AgentArchiveTool.ScopeSummary summary) {
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("汇总：共 ")
                .append(summary.documentCount()).append(" 份正式档案");
        if (summary.personCount() > 0) {
            answer.append("，可识别约 ").append(summary.personCount()).append(" 名学生");
        }
        answer.append("。其中成绩单 ").append(summary.transcriptCount())
                .append(" 份，学籍材料 ").append(summary.studentStatusCount())
                .append(" 份，学位材料 ").append(summary.degreeCount()).append(" 份。");
        if (!summary.materialCounts().isEmpty()) {
            answer.append("材料分布：");
            for (Map.Entry<String, Integer> entry : summary.materialCounts().entrySet()) {
                answer.append(entry.getKey()).append(" ").append(entry.getValue()).append(" 份；");
            }
        }
        if (!summary.sampleReferences().isEmpty()) {
            answer.append("样例档案：");
            appendReferences(answer, summary.sampleReferences(), 5);
        }
        return answer.toString();
    }

    private String yearDistributionAnswer(AgentResolvedScope scope, AgentArchiveTool.YearDistribution distribution) {
        StringBuilder answer = new StringBuilder();
        answer.append(scopeText(scope)).append("共有 ")
                .append(distribution.documentCount())
                .append(" 份正式档案。");
        if (distribution.yearCounts().isEmpty()) {
            answer.append("我没有从题名、目录或档号中识别到明确年份。");
            return answer.toString();
        }
        answer.append("从题名、目录和档号可识别到 ")
                .append(distribution.yearCounts().size())
                .append(" 个年份：");
        distribution.yearCounts().forEach((year, count) ->
                answer.append(year).append(" 年 ").append(count).append(" 份；"));
        answer.append("以上是元数据统计，不依赖 OCR 正文。");
        return answer.toString();
    }

    private MissingDraft missingDraft(AgentResolvedScope scope) {
        AgentArchiveTool.MissingMaterialResult result = archiveTool.checkMissingMaterials(scope);
        List<AgentDocumentReference> references = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("做了轻量缺件核验。");
        if (result.personCount() == 0) {
            answer.append("当前范围内还没有足够的学生姓名线索，无法判断缺件情况。");
            return new MissingDraft(answer.toString(), List.of(), result);
        }
        answer.append("可识别约 ").append(result.personCount()).append(" 名学生，其中 ")
                .append(result.missingPersonCount()).append(" 名存在疑似缺件风险。");
        if (!result.missingPeople().isEmpty()) {
            answer.append("前几项风险：");
            for (AgentArchiveTool.MissingPerson person : result.missingPeople().stream().limit(10).toList()) {
                answer.append(person.name()).append("疑似缺");
                List<String> missing = new ArrayList<>();
                if (person.missingTranscript()) {
                    missing.add("成绩单");
                }
                if (person.missingStudentStatus()) {
                    missing.add("学籍材料");
                }
                if (person.missingDegree()) {
                    missing.add("学位材料");
                }
                answer.append(String.join("、", missing)).append("；");
                references.addAll(person.references());
            }
        }
        answer.append("以上仅根据当前系统可见文件名、标签摘要和 OCR 文本判断，建议人工复核。");
        return new MissingDraft(answer.toString(), references.stream().limit(20).toList(), result);
    }

    private String discussDraft(String message, AgentResolvedScope scope, AgentArchiveContentTool.DiscussResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("讨论档案内容。");
        if (!result.keywords().isEmpty()) {
            answer.append("关注关键词：").append(String.join("、", result.keywords())).append("。");
        }
        if (result.snippets().isEmpty()) {
            answer.append("当前范围内没有找到可用于回答的档案正文、摘要或 OCR 片段。");
            return answer.toString();
        }
        answer.append("共选取 ").append(result.snippets().size()).append(" 份相关档案片段作为依据：");
        for (AgentArchiveContentTool.ContentSnippet snippet : result.snippets().stream().limit(5).toList()) {
            answer.append(snippet.title()).append("：").append(snippet.snippet()).append("；");
        }
        answer.append("请只基于这些片段回答用户问题：").append(message);
        return answer.toString();
    }

    private String capabilityText() {
        return """
                我可以帮你做这些事：
                1. 按自然语言线索查档案，比如姓名、档号、年份、目录、材料类型。
                2. 在当前文件夹内做统计，比如文件数量、年份分布、材料分布。
                3. 基于已完成 OCR 或摘要的档案回答内容问题，并说明证据来自题名、目录还是正文。
                4. 做轻量缺件核验，比如疑似缺成绩单、学籍或学位材料。
                5. 当档案还没有文本索引时，我会提示只能基于题名和目录判断。
                """;
    }

    private boolean isCountOnlyQuestion(String message) {
        String text = message == null ? "" : message;
        return text.contains("多少个文件")
                || text.contains("多少文件")
                || text.contains("文件数")
                || text.contains("文件数量")
                || text.contains("多少份档案")
                || text.contains("档案数")
                || text.contains("档案数量");
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private void appendReferences(StringBuilder answer, List<AgentDocumentReference> references, int limit) {
        int index = 1;
        for (AgentDocumentReference reference : references.stream().limit(limit).toList()) {
            answer.append(index++).append(". ")
                    .append(reference.title())
                    .append(reference.folderPath() == null || reference.folderPath().isBlank() ? "" : "（" + reference.folderPath() + "）")
                    .append("；");
        }
    }

    private String scopeText(AgentResolvedScope scope) {
        if (scope.scopeType() == AgentScopeType.FOLDER) {
            return "当前文件夹“" + scope.folderPath() + "”及其子目录";
        }
        if (scope.scopeType() == AgentScopeType.DOCUMENT) {
            return "当前档案";
        }
        if (scope.scopeType() == AgentScopeType.TASK) {
            return "当前处理任务";
        }
        if (scope.hallId() != null) {
            return "当前馆区";
        }
        return "全局正式档案库";
    }

    private void saveMessage(Long sessionId, String role, String content, AgentIntent intent, Object clientContext, Object resolvedScope) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setClientContextJson(toJson(clientContext));
        message.setResolvedScopeJson(toJson(resolvedScope));
        message.setCreatedAt(LocalDateTime.now());
        agentMessageMapper.insert(message);
    }

    private void touchSession(AgentSession session, String message) {
        session.setTitle(buildTitle(message));
        session.setUpdatedAt(LocalDateTime.now());
        agentSessionMapper.updateById(session);
    }

    private String buildTitle(String message) {
        String text = message == null ? "新对话" : message.trim();
        if (text.isBlank()) {
            return "新对话";
        }
        return text.length() <= 30 ? text : text.substring(0, 30);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private record MissingDraft(String answer, List<AgentDocumentReference> references,
                                AgentArchiveTool.MissingMaterialResult result) {
    }
}
