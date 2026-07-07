package com.danganguan.archive.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "实时事件", description = "前端订阅档案任务、源文件、工作区和导入进度变化")
@RestController
@RequiredArgsConstructor
public class ArchiveRealtimeEventController {
    private final ArchiveRealtimeEventPublisher eventPublisher;

    @Operation(summary = "订阅实时事件", description = "SSE 长连接，前端收到事件后刷新对应任务的源文件、工作区档案或导入任务状态")
    @GetMapping(value = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return eventPublisher.subscribe();
    }
}
