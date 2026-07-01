package com.danganguan.archive.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.task.dto.CreateTaskRequest;
import com.danganguan.archive.task.entity.ArchiveTask;

public interface ArchiveTaskService extends IService<ArchiveTask> {
    ArchiveTask create(CreateTaskRequest request);

    void deleteTask(Long id);
}
