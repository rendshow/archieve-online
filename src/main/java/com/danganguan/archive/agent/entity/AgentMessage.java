package com.danganguan.archive.agent.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;

import java.time.LocalDateTime;

@TableName("agent_message")
@Getter
@Setter
public class AgentMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private AgentTaskIntent intent;
    private String clientContextJson;
    private String resolvedScopeJson;
    private LocalDateTime createdAt;








}
