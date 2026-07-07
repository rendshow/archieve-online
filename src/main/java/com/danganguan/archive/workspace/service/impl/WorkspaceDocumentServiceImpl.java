package com.danganguan.archive.workspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.analysis.service.DocumentAnalyzeService;
import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.ai.dto.AiTaggingRequest;
import com.danganguan.archive.ai.service.AiNamingService;
import com.danganguan.archive.ai.service.AiTaggingService;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.event.ArchiveRealtimeEvent;
import com.danganguan.archive.event.ArchiveRealtimeEventPublisher;
import com.danganguan.archive.document.process.DocumentProcessingService;
import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.service.UploadedFileService;
import com.danganguan.archive.tag.enums.DocumentType;
import com.danganguan.archive.tag.enums.TagSource;
import com.danganguan.archive.tag.service.DocumentTagService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.service.ArchiveTaskService;
import com.danganguan.archive.workspace.dto.UpdateWorkspaceNameRequest;
import com.danganguan.archive.workspace.dto.WorkspaceDocumentQuery;
import com.danganguan.archive.workspace.entity.NamingLog;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.enums.WorkspaceDocumentStatus;
import com.danganguan.archive.workspace.mapper.WorkspaceDocumentMapper;
import com.danganguan.archive.workspace.naming.DefaultWorkspaceNamingService;
import com.danganguan.archive.workspace.service.NamingLogService;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkspaceDocumentServiceImpl extends ServiceImpl<WorkspaceDocumentMapper, WorkspaceDocument>
        implements WorkspaceDocumentService {
    private final ArchiveTaskService archiveTaskService;
    private final UploadedFileService uploadedFileService;
    private final AiNamingService aiNamingService;
    private final AiTaggingService aiTaggingService;
    private final DocumentTagService documentTagService;
    private final NamingLogService namingLogService;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentAnalyzeService documentAnalyzeService;
    private final DefaultWorkspaceNamingService defaultWorkspaceNamingService;
    private final ArchiveRealtimeEventPublisher eventPublisher;

    @Override
    public List<WorkspaceDocument> processTask(Long taskId) {
        return processTask(taskId, List.of());
    }

    @Override
    public List<WorkspaceDocument> processTask(Long taskId, List<Long> fileIds) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        boolean explicitFileBatch = fileIds != null && !fileIds.isEmpty();
        if (task.getStatus() == TaskStatus.PROCESSING && !explicitFileBatch) {
            return listByTask(taskId);
        }
        if ((task.getStatus() == TaskStatus.WAITING_REVIEW || task.getStatus() == TaskStatus.COMPLETED)
                && !explicitFileBatch && hasNoPendingSavedFiles(taskId)) {
            return listByTask(taskId);
        }
        if (aiNamingEnabled(task)) {
            validateNamingReference(task);
        }
        List<UploadedFile> files = explicitFileBatch ? loadQueuedFiles(taskId, fileIds) : claimSavedFiles(taskId);
        if (files.isEmpty()) {
            List<WorkspaceDocument> existing = listByTask(taskId);
            if (!existing.isEmpty()) {
                refreshTaskStatusAfterSuccess(task);
                return existing;
            }
            if (explicitFileBatch) {
                return existing;
            }
            throw new BizException("任务下没有可处理的新增上传文件");
        }

        task.setStatus(TaskStatus.PROCESSING);
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);
        markFilesStatus(files, UploadFileStatus.PROCESSING, null);
        eventPublisher.sourceFilesChanged(task.getId(), task.getHallId(), files, UploadFileStatus.PROCESSING.name(), "原始文件开始处理");
        eventPublisher.publish(ArchiveRealtimeEvent.taskChanged(
                task.getId(), task.getHallId(), task.getStatus().name(), "上传任务开始处理"));

        try {
            List<WorkspaceDocument> documents = new ArrayList<>();
            for (List<UploadedFile> groupFiles : groupFiles(files).values()) {
                UploadedFile firstFile = groupFiles.get(0);
                WorkspaceDocument existing = lambdaQuery()
                        .eq(WorkspaceDocument::getTaskId, taskId)
                        .eq(WorkspaceDocument::getSourceFileId, firstFile.getId())
                        .last("LIMIT 1")
                        .one();
                if (existing != null) {
                    markFilesProcessed(groupFiles);
                    continue;
                }
                documents.addAll(createWorkspaceDocuments(task, groupFiles));
                markFilesProcessed(groupFiles);
            }

            refreshTaskStatusAfterSuccess(task);
            eventPublisher.workspaceDocumentsChanged(task.getId(), task.getHallId(), documents,
                    task.getStatus().name(), "工作区档案已生成");
            eventPublisher.sourceFilesChanged(task.getId(), task.getHallId(), files,
                    UploadFileStatus.PROCESSED.name(), "原始文件处理完成");
            eventPublisher.publish(ArchiveRealtimeEvent.taskChanged(
                    task.getId(), task.getHallId(), task.getStatus().name(), "上传任务处理完成"));
            return documents;
        } catch (RuntimeException ex) {
            markFilesStatus(files, UploadFileStatus.FAILED, limit(ex.getMessage(), 1000));
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(limit(ex.getMessage(), 1000));
            task.setUpdatedAt(LocalDateTime.now());
            archiveTaskService.updateById(task);
            eventPublisher.sourceFilesChanged(task.getId(), task.getHallId(), files,
                    UploadFileStatus.FAILED.name(), "原始文件处理失败");
            eventPublisher.publish(ArchiveRealtimeEvent.taskChanged(
                    task.getId(), task.getHallId(), task.getStatus().name(), "上传任务处理失败"));
            throw ex;
        }
    }

    @Override
    public List<WorkspaceDocument> listByTask(Long taskId) {
        return lambdaQuery().eq(WorkspaceDocument::getTaskId, taskId).orderByDesc(WorkspaceDocument::getCreatedAt).list();
    }

    @Override
    public IPage<WorkspaceDocument> pageDocuments(WorkspaceDocumentQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? 20 : query.size();
        LambdaQueryWrapper<WorkspaceDocument> wrapper = new LambdaQueryWrapper<WorkspaceDocument>()
                .eq(query.hallId() != null, WorkspaceDocument::getHallId, query.hallId())
                .eq(query.taskId() != null, WorkspaceDocument::getTaskId, query.taskId())
                .like(query.folderName() != null && !query.folderName().isBlank(), WorkspaceDocument::getFolderName, query.folderName())
                .orderByDesc(WorkspaceDocument::getCreatedAt);

        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            wrapper.and(inner -> inner
                    .like(WorkspaceDocument::getSuggestedName, keyword)
                    .or()
                    .like(WorkspaceDocument::getFinalName, keyword)
                    .or()
                    .like(WorkspaceDocument::getFolderName, keyword)
                    .or()
                    .like(WorkspaceDocument::getAiSummary, keyword)
                    .or()
                    .like(WorkspaceDocument::getOcrText, keyword));
        }

        if (query.tagId() != null || (query.tagName() != null && !query.tagName().isBlank())) {
            List<Long> documentIds = documentTagService.findDocumentIds(DocumentType.WORKSPACE, query.tagId(), query.tagName());
            if (documentIds.isEmpty()) {
                return Page.of(page, size);
            }
            wrapper.in(WorkspaceDocument::getId, documentIds);
        }

        return page(Page.of(page, size), wrapper);
    }

    @Override
    public TaskStatus processStatus(Long taskId) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        return task.getStatus();
    }

    @Override
    public WorkspaceDocument updateName(Long id, UpdateWorkspaceNameRequest request) {
        if (request.finalName() == null || request.finalName().isBlank()) {
            throw new BizException("文件名不能为空");
        }
        WorkspaceDocument document = getById(id);
        if (document == null) {
            throw new BizException("工作区文件不存在");
        }
        document.setFinalName(request.finalName().trim());
        document.setUpdatedAt(LocalDateTime.now());
        updateById(document);
        return document;
    }

    private Map<String, List<UploadedFile>> groupFiles(List<UploadedFile> files) {
        Map<String, List<UploadedFile>> groups = new LinkedHashMap<>();
        for (UploadedFile file : files) {
            String groupNo = file.getUploadGroupNo() == null || file.getUploadGroupNo().isBlank()
                    ? "LEGACY-" + file.getId()
                    : file.getUploadGroupNo();
            groups.computeIfAbsent(groupNo, ignored -> new ArrayList<>()).add(file);
        }
        return groups;
    }

    private boolean hasNoPendingSavedFiles(Long taskId) {
        return uploadedFileService.lambdaQuery()
                .eq(UploadedFile::getTaskId, taskId)
                .eq(UploadedFile::getStatus, UploadFileStatus.SAVED)
                .count() == 0;
    }

    private List<UploadedFile> claimSavedFiles(Long taskId) {
        List<Long> fileIds = uploadedFileService.listByTask(taskId).stream()
                .filter(file -> file.getStatus() == UploadFileStatus.SAVED)
                .map(UploadedFile::getId)
                .toList();
        if (fileIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        uploadedFileService.lambdaUpdate()
                .eq(UploadedFile::getTaskId, taskId)
                .in(UploadedFile::getId, fileIds)
                .eq(UploadedFile::getStatus, UploadFileStatus.SAVED)
                .set(UploadedFile::getStatus, UploadFileStatus.QUEUED)
                .set(UploadedFile::getUpdatedAt, now)
                .update();
        return loadQueuedFiles(taskId, fileIds);
    }

    private List<UploadedFile> loadQueuedFiles(Long taskId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return uploadedFileService.listByIds(fileIds).stream()
                .filter(file -> file.getTaskId().equals(taskId))
                .filter(file -> file.getStatus() == UploadFileStatus.QUEUED
                        || file.getStatus() == UploadFileStatus.PROCESSING)
                .sorted(Comparator
                        .comparing(UploadedFile::getUploadGroupNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(UploadedFile::getGroupOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(UploadedFile::getId))
                .toList();
    }

    private void markFilesProcessed(List<UploadedFile> files) {
        markFilesStatus(files, UploadFileStatus.PROCESSED, null);
    }

    private void markFilesStatus(List<UploadedFile> files, UploadFileStatus status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        for (UploadedFile file : files) {
            file.setStatus(status);
            file.setErrorMessage(errorMessage);
            file.setUpdatedAt(now);
            uploadedFileService.updateById(file);
        }
    }

    private void refreshTaskStatusAfterSuccess(ArchiveTask task) {
        long activeCount = uploadedFileService.lambdaQuery()
                .eq(UploadedFile::getTaskId, task.getId())
                .in(UploadedFile::getStatus, List.of(UploadFileStatus.QUEUED, UploadFileStatus.PROCESSING))
                .count();
        task.setStatus(activeCount > 0 ? TaskStatus.PROCESSING : TaskStatus.WAITING_REVIEW);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);
    }

    private List<WorkspaceDocument> createWorkspaceDocuments(ArchiveTask task, List<UploadedFile> groupFiles) {
        UploadedFile firstFile = groupFiles.get(0);
        List<ProcessedFileResult> processedFiles = documentProcessingService.processGroup(task, groupFiles);
        List<WorkspaceDocument> documents = new ArrayList<>();
        for (ProcessedFileResult processedFile : processedFiles) {
            DocumentAnalyzeResult analyzeResult = aiNamingEnabled(task)
                    ? documentAnalyzeService.analyze(new DocumentAnalyzeRequest(task, groupFiles, processedFile))
                    : null;
            documents.add(createWorkspaceDocument(task, firstFile, processedFile, analyzeResult));
        }
        return documents;
    }

    private WorkspaceDocument createWorkspaceDocument(ArchiveTask task, UploadedFile file, ProcessedFileResult processedFile,
                                                     DocumentAnalyzeResult analyzeResult) {
        boolean aiNamingEnabled = aiNamingEnabled(task);
        int sequenceNo = nextNamingSequence(task.getId());
        AiNamingResult naming = aiNamingEnabled
                ? aiNamingService.name(new AiNamingRequest(task, file, analyzeResult, sequenceNo))
                : defaultWorkspaceNamingService.name(task, file, sequenceNo);
        LocalDateTime now = LocalDateTime.now();
        String suggestedName = appendSuffix(naming.suggestedName(), processedFile.nameSuffix());

        WorkspaceDocument document = new WorkspaceDocument();
        document.setTaskId(task.getId());
        document.setHallId(task.getHallId());
        document.setSourceFileId(file.getId());
        document.setSuggestedName(suggestedName);
        document.setFinalName(suggestedName);
        document.setFolderName(naming.folderName());
        document.setOutputFormat(processedFile.outputFormat());
        document.setStoragePath(processedFile.storagePath());
        document.setPageCount(processedFile.pageCount());
        document.setAiSummary(naming.summary());
        document.setOcrText(analyzeResult == null ? null : analyzeResult.extractedText());
        document.setNamingReason(naming.reason());
        document.setStatus(WorkspaceDocumentStatus.PENDING_REVIEW);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setDeleted(0);
        save(document);

        if (aiNamingEnabled) {
            List<String> tags = aiTaggingService.tag(new AiTaggingRequest(task, file, suggestedName, analyzeResult)).tags();
            documentTagService.replaceTags(DocumentType.WORKSPACE, document.getId(), tags, TagSource.AI);
        }
        saveNamingLog(task, file, document, naming, suggestedName);
        return document;
    }

    private void saveNamingLog(ArchiveTask task, UploadedFile file, WorkspaceDocument document, AiNamingResult naming, String suggestedName) {
        NamingLog log = new NamingLog();
        log.setTaskId(task.getId());
        log.setSourceFileId(file.getId());
        log.setWorkspaceDocumentId(document.getId());
        log.setUserReference(task.getFileNameExample());
        log.setHistoryReference(task.getNamingSource());
        log.setAiSuggestedName(suggestedName);
        log.setFinalName(document.getFinalName());
        log.setNamingReason(naming.reason());
        log.setAllowAiOverride(task.getAllowAiOverride());
        log.setCreatedAt(LocalDateTime.now());
        namingLogService.save(log);
    }

    private int nextNamingSequence(Long taskId) {
        return Math.toIntExact(lambdaQuery().eq(WorkspaceDocument::getTaskId, taskId).count()) + 1;
    }

    private String appendSuffix(String name, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return name;
        }
        return name + suffix;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean aiNamingEnabled(ArchiveTask task) {
        return Boolean.TRUE.equals(task.getAllowAiOverride());
    }

    private void validateNamingReference(ArchiveTask task) {
        boolean hasFileExample = task.getFileNameExample() != null && !task.getFileNameExample().isBlank();
        boolean hasFolderExample = task.getFolderNameExample() != null && !task.getFolderNameExample().isBlank();
        boolean useHistory = task.getNamingSource() != null && task.getNamingSource().equalsIgnoreCase("HISTORY");
        if (!hasFileExample && !hasFolderExample && !useHistory) {
            throw new BizException("缺少命名参考：请填写文件命名示例、文件夹命名示例，或选择参考历史任务");
        }
    }
}
