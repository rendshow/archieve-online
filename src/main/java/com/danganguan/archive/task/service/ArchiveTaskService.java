package com.danganguan.archive.task.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.task.dto.CreateTaskRequest;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.ConvertStrategy;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.mapper.ArchiveTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ArchiveTaskService extends ServiceImpl<ArchiveTaskMapper, ArchiveTask> {

    public ArchiveTask create(CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ArchiveTask task = new ArchiveTask();
        task.setTaskNo("TASK" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        task.setHallId(request.hallId());
        task.setTaskName(request.taskName());
        task.setNamingSource(request.namingSource());
        task.setFolderNameExample(request.folderNameExample());
        task.setFileNameExample(request.fileNameExample());
        task.setAllowAiOverride(Boolean.TRUE.equals(request.allowAiOverride()));
        task.setEnableScanEnhance(Boolean.TRUE.equals(request.enableScanEnhance()));
        task.setConvertStrategy(request.convertStrategy() == null ? ConvertStrategy.ONE_TO_ONE : request.convertStrategy());
        task.setFixedSplitCount(request.fixedSplitCount());
        task.setOutputFormat(request.outputFormat() == null ? OutputFormat.PDF : request.outputFormat());
        task.setStatus(TaskStatus.DRAFT);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setDeleted(0);
        save(task);
        return task;
    }
}
