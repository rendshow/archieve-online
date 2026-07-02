package com.danganguan.archive.workspace.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.workspace.dto.UpdateWorkspaceNameRequest;
import com.danganguan.archive.workspace.dto.WorkspaceDocumentQuery;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工作区档案", description = "工作区处理、审核前档案接口")
@RestController
public class WorkspaceDocumentController {
    private final WorkspaceDocumentService workspaceDocumentService;

    public WorkspaceDocumentController(WorkspaceDocumentService workspaceDocumentService) {
        this.workspaceDocumentService = workspaceDocumentService;
    }

    @Operation(summary = "处理上传任务", description = "将任务下的原始文件处理为工作区档案，并触发 mock AI 命名和标签生成")
    @PostMapping("/api/tasks/{taskId}/process")
    public Result<List<WorkspaceDocument>> process(@PathVariable Long taskId) {
        return Result.ok(workspaceDocumentService.processTask(taskId));
    }

    @Operation(summary = "查询处理状态", description = "查询指定上传任务当前处理状态")
    @GetMapping("/api/tasks/{taskId}/process-status")
    public Result<TaskStatus> processStatus(@PathVariable Long taskId) {
        return Result.ok(workspaceDocumentService.processStatus(taskId));
    }

    @Operation(summary = "查询工作区档案", description = "查询指定任务下生成的工作区档案列表")
    @GetMapping("/api/tasks/{taskId}/workspace-documents")
    public Result<List<WorkspaceDocument>> listByTask(@PathVariable Long taskId) {
        return Result.ok(workspaceDocumentService.listByTask(taskId));
    }

    @Operation(summary = "分页查询工作区档案", description = "按馆、任务、关键词、文件夹、标签分页查询审核前档案")
    @GetMapping("/api/workspace-documents")
    public Result<IPage<WorkspaceDocument>> page(WorkspaceDocumentQuery query) {
        return Result.ok(workspaceDocumentService.pageDocuments(query));
    }

    @Operation(summary = "修改工作区档案名称", description = "修改工作区档案最终采用的文件名")
    @PutMapping("/api/workspace-documents/{id}/name")
    public Result<WorkspaceDocument> updateName(@PathVariable Long id,
                                                @RequestBody UpdateWorkspaceNameRequest request) {
        return Result.ok(workspaceDocumentService.updateName(id, request));
    }

    @Operation(summary = "删除工作区档案", description = "软删除单个工作区档案")
    @DeleteMapping("/api/workspace-documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workspaceDocumentService.removeById(id);
        return Result.ok();
    }
}
