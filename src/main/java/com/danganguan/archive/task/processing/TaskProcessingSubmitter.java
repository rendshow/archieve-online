package com.danganguan.archive.task.processing;

import com.danganguan.archive.workspace.entity.WorkspaceDocument;

import java.util.List;

public interface TaskProcessingSubmitter {
    List<WorkspaceDocument> submit(Long taskId);
}
