package com.danganguan.archive.agent.controller;

import com.danganguan.archive.agent.dto.AgentChatRequest;
import com.danganguan.archive.agent.dto.AgentChatResponse;
import com.danganguan.archive.agent.service.AgentChatService;
import com.danganguan.archive.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "学生档案检索与核验 Agent", description = "面向学生档案查找、当前范围汇总和缺件风险核验的受控对话接口")
@RestController
@RequiredArgsConstructor
public class AgentController {
    private final AgentChatService agentChatService;

    @Operation(summary = "Agent 对话", description = "前端每次请求需传入实时页面上下文，页面上下文会作为 Agent 查询边界")
    @PostMapping("/api/agent/chat")
    public Result<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return Result.ok(agentChatService.chat(request));
    }

    @Operation(summary = "Agent 流式对话", description = "SSE 流式输出，事件包含 meta、delta、done、error")
    @PostMapping(value = "/api/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentChatRequest request) {
        return agentChatService.stream(request);
    }
}
