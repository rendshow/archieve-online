package com.danganguan.archive.task.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.danganguan.archive.common.response.ApiResponse;
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
    public ApiResponse<ArchiveTask> create(@RequestBody CreateTaskRequest request) {
        return ApiResponse.ok(archiveTaskService.create(request));
    }

    @GetMapping
    public ApiResponse<IPage<ArchiveTask>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(archiveTaskService.page(Page.of(page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArchiveTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(archiveTaskService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        archiveTaskService.removeById(id);
        return ApiResponse.ok();
    }
}
