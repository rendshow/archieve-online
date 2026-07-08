package com.danganguan.archive.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.agent.context.AgentContextResolver;
import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.dto.AgentChatResponse;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.entity.AgentMessage;
import com.danganguan.archive.agent.entity.AgentSession;
import com.danganguan.archive.agent.enums.AgentIntent;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.intent.AgentIntentClassifier;
import com.danganguan.archive.agent.mapper.AgentMessageMapper;
import com.danganguan.archive.agent.mapper.AgentSessionMapper;
import com.danganguan.archive.agent.service.AgentChatService;
import com.danganguan.archive.agent.tool.AgentArchiveTool;
import com.danganguan.archive.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentIntentClassifier intentClassifier;
    private final AgentContextResolver contextResolver;
    private final AgentArchiveTool archiveTool;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentChatResponse chat(AgentChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BizException("消息不能为空");
        }
        AgentSession session = getOrCreateSession(request);
        AgentIntent intent = intentClassifier.classify(request.message());
        AgentResolvedScope scope = contextResolver.resolve(intent, request.clientContext());

        AgentChatResponse response;
        if (isOutOfPageScope(request.message(), scope)) {
            response = outOfScope(session.getId(), scope);
        } else if (needsScope(intent, scope)) {
            response = needScope(session.getId(), intent, scope);
        } else {
            response = switch (intent) {
                case SEARCH_ARCHIVE -> search(session.getId(), request.message(), scope);
                case SUMMARIZE_SCOPE -> summarize(session.getId(), scope);
                case CHECK_MISSING_MATERIALS -> checkMissingMaterials(session.getId(), scope);
                default -> unknown(session.getId(), scope);
            };
        }

        saveMessage(session.getId(), "USER", request.message(), intent, request.clientContext(), scope);
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
        } else {
            answer.append("找到 ").append(result.total()).append(" 条候选档案，前几条包括：");
            appendReferences(answer, result.references(), 8);
        }
        return new AgentChatResponse(sessionId, AgentIntent.SEARCH_ARCHIVE, scope, answer.toString(), result.references());
    }

    private AgentChatResponse summarize(Long sessionId, AgentResolvedScope scope) {
        AgentArchiveTool.ScopeSummary summary = archiveTool.summarize(scope);
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
        return new AgentChatResponse(sessionId, AgentIntent.SUMMARIZE_SCOPE, scope, answer.toString(), summary.sampleReferences());
    }

    private AgentChatResponse checkMissingMaterials(Long sessionId, AgentResolvedScope scope) {
        AgentArchiveTool.MissingMaterialResult result = archiveTool.checkMissingMaterials(scope);
        List<AgentDocumentReference> references = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        answer.append("我按").append(scopeText(scope)).append("做了轻量缺件核验。");
        if (result.personCount() == 0) {
            answer.append("当前范围内还没有足够的学生姓名线索，无法判断缺件情况。");
            return new AgentChatResponse(sessionId, AgentIntent.CHECK_MISSING_MATERIALS, scope, answer.toString(), List.of());
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
        return new AgentChatResponse(sessionId, AgentIntent.CHECK_MISSING_MATERIALS, scope, answer.toString(), references.stream().limit(20).toList());
    }

    private AgentChatResponse outOfScope(Long sessionId, AgentResolvedScope scope) {
        String answer = "当前对话范围限定在" + scopeText(scope)
                + "，不能扩展到页面范围之外查询。请回到全局档案页或对应上级目录后再提问。";
        return new AgentChatResponse(sessionId, AgentIntent.OUT_OF_SCOPE, scope, answer, List.of());
    }

    private AgentChatResponse needScope(Long sessionId, AgentIntent intent, AgentResolvedScope scope) {
        String answer = "这个问题需要明确的目录或馆区范围。请先进入要汇总/核验的文件夹，或在全局档案页选择馆区后再提问。";
        return new AgentChatResponse(sessionId, AgentIntent.NEED_SCOPE, scope, answer, List.of());
    }

    private AgentChatResponse unknown(Long sessionId, AgentResolvedScope scope) {
        String answer = "我目前主要支持三类问题：查找档案、汇总当前范围、核验当前范围是否疑似缺成绩单/学籍/学位材料。你可以换一种更具体的问法。";
        return new AgentChatResponse(sessionId, AgentIntent.UNKNOWN, scope, answer, List.of());
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
        return (intent == AgentIntent.SUMMARIZE_SCOPE || intent == AgentIntent.CHECK_MISSING_MATERIALS)
                && scope.scopeType() == AgentScopeType.GLOBAL
                && scope.hallId() == null;
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
}
