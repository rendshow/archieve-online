package com.danganguan.archive.task.processing.impl;

import com.danganguan.archive.task.processing.TaskProcessingSubmitter;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.processing", name = "mode", havingValue = "sync", matchIfMissing = true)
public class SyncTaskProcessingSubmitter implements TaskProcessingSubmitter {
    private final WorkspaceDocumentService workspaceDocumentService;

    @Override
    public List<WorkspaceDocument> submit(Long taskId) {
        return workspaceDocumentService.processTask(taskId);
    }
}
