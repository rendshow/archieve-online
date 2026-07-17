package com.danganguan.archive.agent.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.entity.AgentMessage;
import com.danganguan.archive.agent.entity.AgentSession;
import com.danganguan.archive.agent.mapper.AgentMessageMapper;
import com.danganguan.archive.agent.mapper.AgentSessionMapper;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import com.danganguan.archive.agent.v2.service.AgentV2ConversationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentV2ConversationServiceImpl implements AgentV2ConversationService {
    private static final int RECENT_MESSAGE_LIMIT = 12;
    private static final List<String> REFERENCE_TERMS = List.of("这份档案", "这个档案", "该档案", "刚才那份档案", "刚才的档案");

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentSession getOrCreate(Long sessionId, String firstMessage) {
        if (sessionId != null) {
            AgentSession session = agentSessionMapper.selectById(sessionId);
            if (session != null) {
                return session;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        AgentSession session = new AgentSession();
        session.setTitle(titleOf(firstMessage));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);
        return session;
    }

    @Override
    public Optional<AgentClientContext> resolveReferencedDocument(Long sessionId, String message,
                                                                   AgentClientContext currentContext,
                                                                   AgentResolvedScope currentScope) {
        if (sessionId == null || currentContext == null || currentContext.documentId() != null || !isReference(message)) {
            return Optional.empty();
        }
        return recentAssistantMessages(sessionId).stream()
                .map(this::readSnapshot)
                .flatMap(Optional::stream)
                .filter(snapshot -> snapshot.documents().size() == 1)
                .map(snapshot -> snapshot.documents().getFirst())
                .filter(document -> belongsToCurrentScope(document, currentScope))
                .findFirst()
                .map(document -> new AgentClientContext(
                        currentContext.pageType(),
                        currentContext.hallId() == null ? document.hallId() : currentContext.hallId(),
                        currentContext.folderPath(),
                        document.documentId(),
                        currentContext.taskId(),
                        List.of(document.documentId()),
                        currentContext.searchKeyword()
                ));
    }

    @Override
    @Transactional
    public void saveTurn(AgentSession session, String userMessage, AgentClientContext clientContext,
                         AgentToolExecutionResult result) {
        saveMessage(session.getId(), "V2_USER", userMessage, clientContext, Map.of("scope", result.task().scope()));
        ConversationSnapshot snapshot = new ConversationSnapshot(result.task().scope(), result.documents());
        saveMessage(session.getId(), "V2_ASSISTANT", result.answer(), clientContext, snapshot);
        session.setUpdatedAt(LocalDateTime.now());
        agentSessionMapper.updateById(session);
    }

    private List<AgentMessage> recentAssistantMessages(Long sessionId) {
        return agentMessageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getRole, "V2_ASSISTANT")
                .orderByDesc(AgentMessage::getCreatedAt)
                .last("LIMIT " + RECENT_MESSAGE_LIMIT));
    }

    private Optional<ConversationSnapshot> readSnapshot(AgentMessage message) {
        if (message.getResolvedScopeJson() == null || message.getResolvedScopeJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(message.getResolvedScopeJson(), ConversationSnapshot.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    private void saveMessage(Long sessionId, String role, String content, AgentClientContext clientContext, Object scope) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setClientContextJson(write(clientContext));
        message.setResolvedScopeJson(write(scope));
        message.setCreatedAt(LocalDateTime.now());
        agentMessageMapper.insert(message);
    }

    private boolean belongsToCurrentScope(AgentDocumentReference document, AgentResolvedScope scope) {
        if (scope == null) {
            return false;
        }
        if (scope.hallId() != null && !scope.hallId().equals(document.hallId())) {
            return false;
        }
        if (scope.folderPath() == null || scope.folderPath().isBlank()) {
            return true;
        }
        return document.folderPath() != null && (document.folderPath().equals(scope.folderPath())
                || document.folderPath().startsWith(scope.folderPath() + "/"));
    }

    private boolean isReference(String message) {
        return message != null && REFERENCE_TERMS.stream().anyMatch(message::contains);
    }

    private String titleOf(String message) {
        if (message == null || message.isBlank()) {
            return "Agent V2 对话";
        }
        return message.length() <= 80 ? message : message.substring(0, 80);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存 Agent V2 会话上下文", ex);
        }
    }

    private record ConversationSnapshot(AgentResolvedScope scope, List<AgentDocumentReference> documents) {
    }
}
