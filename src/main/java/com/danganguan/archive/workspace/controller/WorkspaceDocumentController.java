package com.danganguan.archive.workspace.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.workspace.dto.UpdateWorkspaceNameRequest;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class WorkspaceDocumentController {
    private final WorkspaceDocumentService workspaceDocumentService;

    public WorkspaceDocumentController(WorkspaceDocumentService workspaceDocumentService) {
        this.workspaceDocumentService = workspaceDocumentService;
    }

    @PostMapping("/api/tasks/{taskId}/process")
    public Result<List<WorkspaceDocument>> process(@PathVariable Long taskId) {
        return Result.ok(workspaceDocumentService.processTask(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/process-status")
    public Result<TaskStatus> processStatus(@PathVariable Long taskId) {
        return Result.ok(workspaceDocumentService.processStatus(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/workspace-documents")
    public Result<List<WorkspaceDocument>> listByTask(@PathVariable Long taskId) {
        return Result.ok(workspaceDocumentService.listByTask(taskId));
    }

    @PutMapping("/api/workspace-documents/{id}/name")
    public Result<WorkspaceDocument> updateName(@PathVariable Long id,
                                                @RequestBody UpdateWorkspaceNameRequest request) {
        return Result.ok(workspaceDocumentService.updateName(id, request));
    }

    @DeleteMapping("/api/workspace-documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workspaceDocumentService.removeById(id);
        return Result.ok();
    }
}
