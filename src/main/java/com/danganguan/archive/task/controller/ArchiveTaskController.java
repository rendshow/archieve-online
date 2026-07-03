package com.danganguan.archive.task.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.task.dto.CreateTaskRequest;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.service.ArchiveTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "上传任务", description = "档案上传处理任务接口")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class ArchiveTaskController {
    private final ArchiveTaskService archiveTaskService;

    @Operation(summary = "创建上传任务", description = "创建一次档案上传处理任务，并记录命名参考、转换策略和输出格式")
    @PostMapping
    public Result<ArchiveTask> create(@RequestBody CreateTaskRequest request) {
        return Result.ok(archiveTaskService.create(request));
    }

    @Operation(summary = "分页查询上传任务", description = "分页查询上传任务列表")
    @GetMapping
    public Result<IPage<ArchiveTask>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(archiveTaskService.page(Page.of(page, size)));
    }

    @Operation(summary = "查询上传任务详情", description = "根据任务 ID 查询上传任务详情")
    @GetMapping("/{id}")
    public Result<ArchiveTask> detail(@PathVariable Long id) {
        return Result.ok(archiveTaskService.getById(id));
    }

    @Operation(summary = "删除上传任务", description = "软删除上传任务，并同步软删除任务下的原始文件和工作区文件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        archiveTaskService.deleteTask(id);
        return Result.ok();
    }
}
