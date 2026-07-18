package com.danganguan.archive.agent.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_session")
@Getter
@Setter
public class AgentSession {
    private Long id;
    private String title;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;





}
