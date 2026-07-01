package com.danganguan.archive.task.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.task.dto.CreateTaskRequest;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.service.ArchiveTaskService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class ArchiveTaskController {
    private final ArchiveTaskService archiveTaskService;

    public ArchiveTaskController(ArchiveTaskService archiveTaskService) {
        this.archiveTaskService = archiveTaskService;
    }

    @PostMapping
    public Result<ArchiveTask> create(@RequestBody CreateTaskRequest request) {
        return Result.ok(archiveTaskService.create(request));
    }

    @GetMapping
    public Result<IPage<ArchiveTask>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(archiveTaskService.page(Page.of(page, size)));
    }

    @GetMapping("/{id}")
    public Result<ArchiveTask> detail(@PathVariable Long id) {
        return Result.ok(archiveTaskService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        archiveTaskService.deleteTask(id);
        return Result.ok();
    }
}
