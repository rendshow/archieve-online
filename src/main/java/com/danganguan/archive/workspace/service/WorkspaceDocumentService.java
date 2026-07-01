package com.danganguan.archive.workspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.workspace.dto.UpdateWorkspaceNameRequest;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;

import java.util.List;

public interface WorkspaceDocumentService extends IService<WorkspaceDocument> {
    List<WorkspaceDocument> processTask(Long taskId);

    List<WorkspaceDocument> listByTask(Long taskId);

    TaskStatus processStatus(Long taskId);

    WorkspaceDocument updateName(Long id, UpdateWorkspaceNameRequest request);
}
