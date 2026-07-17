package com.danganguan.archive.agent.v2.service;

import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.entity.AgentSession;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;

import java.util.Optional;

/** Persists the minimum structured context needed for bounded V2 follow-up questions. */
public interface AgentV2ConversationService {
    AgentSession getOrCreate(Long sessionId, String firstMessage);

    Optional<AgentClientContext> resolveReferencedDocument(Long sessionId, String message,
                                                           AgentClientContext currentContext,
                                                           AgentResolvedScope currentScope);

    void saveTurn(AgentSession session, String userMessage, AgentClientContext clientContext,
                  AgentToolExecutionResult result);
}
