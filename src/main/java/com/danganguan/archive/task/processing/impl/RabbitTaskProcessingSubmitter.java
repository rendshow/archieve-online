package com.danganguan.archive.task.processing.impl;

import com.danganguan.archive.common.config.ArchiveStorageProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.processing.TaskProcessMessage;
import com.danganguan.archive.task.processing.TaskProcessingSubmitter;
import com.danganguan.archive.task.service.ArchiveTaskService;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.processing", name = "mode", havingValue = "rabbitmq")
public class RabbitTaskProcessingSubmitter implements TaskProcessingSubmitter {
    private final RabbitTemplate rabbitTemplate;
    private final ArchiveStorageProperties properties;
    private final ArchiveTaskService archiveTaskService;
    private final WorkspaceDocumentService workspaceDocumentService;

    @Override
    public List<WorkspaceDocument> submit(Long taskId) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        task.setStatus(TaskStatus.PENDING_PROCESS);
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);

        ArchiveStorageProperties.Rabbitmq rabbitmq = properties.getProcessing().getRabbitmq();
        rabbitTemplate.convertAndSend(rabbitmq.getExchange(), rabbitmq.getRoutingKey(), new TaskProcessMessage(taskId));
        return workspaceDocumentService.listByTask(taskId);
    }
}
