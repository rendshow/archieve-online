package com.danganguan.archive.agent.v2.service;

import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;

public interface AgentTaskPlanner {
    AgentTaskSpec plan(String message, AgentClientContext clientContext);
}
