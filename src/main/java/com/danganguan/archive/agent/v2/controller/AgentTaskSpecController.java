package com.danganguan.archive.agent.v2.controller;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import com.danganguan.archive.agent.v2.service.AgentTaskPlanner;
import com.danganguan.archive.agent.v2.service.AgentV2ExecutionService;
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
    private final AgentV2ExecutionService agentV2ExecutionService;

    @Operation(summary = "生成 Agent V2 任务说明", description = "返回意图、范围、工具、所需证据与必要澄清，不泄露模型推理过程")
    @PostMapping("/api/agent/v2/task-spec")
    public Result<AgentTaskSpec> plan(@RequestBody AgentChatRequest request) {
        return Result.ok(agentTaskPlanner.plan(request.message(), request.clientContext()));
    }

    @Operation(summary = "执行 Agent V2 只读任务", description = "当前已接入页级证据问答工具；未接入的工具会明确返回未实现，不生成猜测性答案")
    @PostMapping("/api/agent/v2/execute")
    public Result<AgentToolExecutionResult> execute(@RequestBody AgentChatRequest request) {
        return Result.ok(agentV2ExecutionService.execute(request));
    }
}
