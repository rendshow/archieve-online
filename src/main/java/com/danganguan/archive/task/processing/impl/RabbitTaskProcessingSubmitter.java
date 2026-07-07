package com.danganguan.archive.task.processing.impl;

import com.danganguan.archive.common.config.ArchiveStorageProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.service.UploadedFileService;
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
    private final UploadedFileService uploadedFileService;

    @Override
    public List<WorkspaceDocument> submit(Long taskId) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        List<Long> fileIds = savedFileIds(taskId);
        if (fileIds.isEmpty()) {
            List<WorkspaceDocument> existing = workspaceDocumentService.listByTask(taskId);
            if (!existing.isEmpty() || task.getStatus() == TaskStatus.PENDING_PROCESS || task.getStatus() == TaskStatus.PROCESSING) {
                return existing;
            }
            throw new BizException("任务下没有可处理的新增上传文件");
        }

        LocalDateTime now = LocalDateTime.now();
        uploadedFileService.lambdaUpdate()
                .eq(UploadedFile::getTaskId, taskId)
                .in(UploadedFile::getId, fileIds)
                .eq(UploadedFile::getStatus, UploadFileStatus.SAVED)
                .set(UploadedFile::getStatus, UploadFileStatus.QUEUED)
                .set(UploadedFile::getUpdatedAt, now)
                .update();

        List<Long> queuedFileIds = uploadedFileService.listByIds(fileIds).stream()
                .filter(file -> file.getTaskId().equals(taskId))
                .filter(file -> file.getStatus() == UploadFileStatus.QUEUED)
                .map(UploadedFile::getId)
                .toList();
        if (queuedFileIds.isEmpty()) {
            return workspaceDocumentService.listByTask(taskId);
        }

        if (task.getStatus() != TaskStatus.PROCESSING) {
            task.setStatus(TaskStatus.PENDING_PROCESS);
        }
        task.setErrorMessage(null);
        task.setUpdatedAt(now);
        archiveTaskService.updateById(task);

        ArchiveStorageProperties.Rabbitmq rabbitmq = properties.getProcessing().getRabbitmq();
        rabbitTemplate.convertAndSend(rabbitmq.getExchange(), rabbitmq.getRoutingKey(), new TaskProcessMessage(taskId, queuedFileIds));
        return workspaceDocumentService.listByTask(taskId);
    }

    private List<Long> savedFileIds(Long taskId) {
        return uploadedFileService.listByTask(taskId).stream()
                .filter(file -> file.getStatus() == UploadFileStatus.SAVED)
                .map(UploadedFile::getId)
                .toList();
    }
}
