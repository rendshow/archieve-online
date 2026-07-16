package com.danganguan.archive.agent.v2.controller;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.service.AgentTaskPlanner;
import com.danganguan.archive.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent V2 任务规划", description = "将用户问题规划为受控只读工具任务，不执行写操作")
@RestController
@RequiredArgsConstructor
public class AgentTaskSpecController {
    private final AgentTaskPlanner agentTaskPlanner;

    @Operation(summary = "生成 Agent V2 任务说明", description = "返回意图、范围、工具、所需证据与必要澄清，不泄露模型推理过程")
    @PostMapping("/api/agent/v2/task-spec")
    public Result<AgentTaskSpec> plan(@RequestBody AgentChatRequest request) {
        return Result.ok(agentTaskPlanner.plan(request.message(), request.clientContext()));
    }
}
