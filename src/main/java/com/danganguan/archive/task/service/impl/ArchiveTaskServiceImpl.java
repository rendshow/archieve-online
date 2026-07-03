package com.danganguan.archive.task.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.mapper.UploadedFileMapper;
import com.danganguan.archive.task.dto.CreateTaskRequest;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.mapper.ArchiveTaskMapper;
import com.danganguan.archive.task.service.ArchiveTaskService;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.enums.WorkspaceDocumentStatus;
import com.danganguan.archive.workspace.mapper.WorkspaceDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ArchiveTaskServiceImpl extends ServiceImpl<ArchiveTaskMapper, ArchiveTask> implements ArchiveTaskService {
    private final UploadedFileMapper uploadedFileMapper;
    private final WorkspaceDocumentMapper workspaceDocumentMapper;

    @Override
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
        PersonSplitStrategy splitStrategy = request.personSplitStrategy() == null
                ? PersonSplitStrategy.SINGLE_PERSON
                : request.personSplitStrategy();
        OutputFormat outputFormat = request.outputFormat() == null ? OutputFormat.PDF : request.outputFormat();
        validateProcessingOptions(outputFormat, splitStrategy, request.fixedElementsPerPerson());
        task.setPersonSplitStrategy(splitStrategy);
        task.setFixedElementsPerPerson(request.fixedElementsPerPerson());
        task.setOutputFormat(outputFormat);
        task.setStatus(TaskStatus.DRAFT);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setDeleted(0);
        save(task);
        return task;
    }

    @Override
    @Transactional
    public void deleteTask(Long id) {
        removeById(id);
        LocalDateTime now = LocalDateTime.now();
        uploadedFileMapper.update(null, Wrappers.<UploadedFile>lambdaUpdate()
                .eq(UploadedFile::getTaskId, id)
                .set(UploadedFile::getStatus, UploadFileStatus.DELETED)
                .set(UploadedFile::getUpdatedAt, now)
                .set(UploadedFile::getDeleted, 1));
        workspaceDocumentMapper.update(null, Wrappers.<WorkspaceDocument>lambdaUpdate()
                .eq(WorkspaceDocument::getTaskId, id)
                .set(WorkspaceDocument::getStatus, WorkspaceDocumentStatus.DELETED)
                .set(WorkspaceDocument::getUpdatedAt, now)
                .set(WorkspaceDocument::getDeleted, 1));
    }

    private void validateProcessingOptions(OutputFormat outputFormat, PersonSplitStrategy splitStrategy, Integer fixedElementsPerPerson) {
        if (outputFormat == OutputFormat.PNG && !splitStrategy.isSinglePerson()) {
            throw new BizException("输出为图片时仅支持单人单组策略");
        }
        if (outputFormat == OutputFormat.PDF && splitStrategy.isFixedElementsPerPerson()
                && (fixedElementsPerPerson == null || fixedElementsPerPerson <= 0)) {
            throw new BizException("输出为 PDF 且使用固定元素拆分时，每人元素数必须大于0");
        }
    }
}
