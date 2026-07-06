package com.danganguan.archive.task.processing.impl;

import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.processing.TaskProcessMessage;
import com.danganguan.archive.task.service.ArchiveTaskService;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "archive.processing", name = "mode", havingValue = "rabbitmq")
public class RabbitTaskProcessingConsumer {
    private final WorkspaceDocumentService workspaceDocumentService;
    private final ArchiveTaskService archiveTaskService;

    @RabbitListener(queues = "${archive.processing.rabbitmq.queue}")
    public void process(TaskProcessMessage message) {
        try {
            workspaceDocumentService.processTask(message.taskId());
        } catch (Exception ex) {
            ArchiveTask task = archiveTaskService.getById(message.taskId());
            if (task != null) {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage(limit(ex.getMessage(), 1000));
                task.setUpdatedAt(LocalDateTime.now());
                archiveTaskService.updateById(task);
            }
            log.error("异步处理上传任务失败，taskId={}", message.taskId(), ex);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
