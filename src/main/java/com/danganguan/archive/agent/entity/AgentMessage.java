package com.danganguan.archive.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.agent.enums.AgentIntent;

import java.time.LocalDateTime;

@TableName("agent_message")
public class AgentMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private AgentIntent intent;
    private String clientContextJson;
    private String resolvedScopeJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public AgentIntent getIntent() { return intent; }
    public void setIntent(AgentIntent intent) { this.intent = intent; }
    public String getClientContextJson() { return clientContextJson; }
    public void setClientContextJson(String clientContextJson) { this.clientContextJson = clientContextJson; }
    public String getResolvedScopeJson() { return resolvedScopeJson; }
    public void setResolvedScopeJson(String resolvedScopeJson) { this.resolvedScopeJson = resolvedScopeJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
