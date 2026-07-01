package com.danganguan.archive.workspace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.ai.dto.AiTaggingRequest;
import com.danganguan.archive.ai.service.AiNamingService;
import com.danganguan.archive.ai.service.AiTaggingService;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.service.UploadedFileService;
import com.danganguan.archive.tag.enums.DocumentType;
import com.danganguan.archive.tag.enums.TagSource;
import com.danganguan.archive.tag.service.DocumentTagService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.service.ArchiveTaskService;
import com.danganguan.archive.workspace.dto.UpdateWorkspaceNameRequest;
import com.danganguan.archive.workspace.entity.NamingLog;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.enums.WorkspaceDocumentStatus;
import com.danganguan.archive.workspace.mapper.WorkspaceDocumentMapper;
import com.danganguan.archive.workspace.service.NamingLogService;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkspaceDocumentServiceImpl extends ServiceImpl<WorkspaceDocumentMapper, WorkspaceDocument>
        implements WorkspaceDocumentService {
    private final ArchiveTaskService archiveTaskService;
    private final UploadedFileService uploadedFileService;
    private final AiNamingService aiNamingService;
    private final AiTaggingService aiTaggingService;
    private final DocumentTagService documentTagService;
    private final NamingLogService namingLogService;

    public WorkspaceDocumentServiceImpl(ArchiveTaskService archiveTaskService,
                                        UploadedFileService uploadedFileService,
                                        AiNamingService aiNamingService,
                                        AiTaggingService aiTaggingService,
                                        DocumentTagService documentTagService,
                                        NamingLogService namingLogService) {
        this.archiveTaskService = archiveTaskService;
        this.uploadedFileService = uploadedFileService;
        this.aiNamingService = aiNamingService;
        this.aiTaggingService = aiTaggingService;
        this.documentTagService = documentTagService;
        this.namingLogService = namingLogService;
    }

    @Override
    @Transactional
    public List<WorkspaceDocument> processTask(Long taskId) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        validateNamingReference(task);
        List<UploadedFile> files = uploadedFileService.listByTask(taskId);
        if (files.isEmpty()) {
            throw new BizException("任务下没有可处理的上传文件");
        }

        task.setStatus(TaskStatus.PROCESSING);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);

        List<WorkspaceDocument> documents = new ArrayList<>();
        for (UploadedFile file : files) {
            WorkspaceDocument existing = lambdaQuery()
                    .eq(WorkspaceDocument::getTaskId, taskId)
                    .eq(WorkspaceDocument::getSourceFileId, file.getId())
                    .one();
            if (existing != null) {
                documents.add(existing);
                continue;
            }
            documents.add(createWorkspaceDocument(task, file));
        }

        task.setStatus(TaskStatus.WAITING_REVIEW);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);
        return documents;
    }

    @Override
    public List<WorkspaceDocument> listByTask(Long taskId) {
        return lambdaQuery().eq(WorkspaceDocument::getTaskId, taskId).orderByDesc(WorkspaceDocument::getCreatedAt).list();
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

    private WorkspaceDocument createWorkspaceDocument(ArchiveTask task, UploadedFile file) {
        AiNamingResult naming = aiNamingService.name(new AiNamingRequest(task, file));
        LocalDateTime now = LocalDateTime.now();

        WorkspaceDocument document = new WorkspaceDocument();
        document.setTaskId(task.getId());
        document.setHallId(task.getHallId());
        document.setSourceFileId(file.getId());
        document.setSuggestedName(naming.suggestedName());
        document.setFinalName(naming.suggestedName());
        document.setFolderName(naming.folderName());
        document.setOutputFormat(task.getOutputFormat());
        document.setStoragePath(file.getStoragePath());
        document.setPageCount(1);
        document.setAiSummary(naming.summary());
        document.setNamingReason(naming.reason());
        document.setStatus(WorkspaceDocumentStatus.PENDING_REVIEW);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setDeleted(0);
        save(document);

        List<String> tags = aiTaggingService.tag(new AiTaggingRequest(task, file, naming.suggestedName())).tags();
        documentTagService.replaceTags(DocumentType.WORKSPACE, document.getId(), tags, TagSource.AI);
        saveNamingLog(task, file, document, naming);
        return document;
    }

    private void saveNamingLog(ArchiveTask task, UploadedFile file, WorkspaceDocument document, AiNamingResult naming) {
        NamingLog log = new NamingLog();
        log.setTaskId(task.getId());
        log.setSourceFileId(file.getId());
        log.setWorkspaceDocumentId(document.getId());
        log.setUserReference(task.getFileNameExample());
        log.setHistoryReference(task.getNamingSource());
        log.setAiSuggestedName(naming.suggestedName());
        log.setFinalName(document.getFinalName());
        log.setNamingReason(naming.reason());
        log.setAllowAiOverride(task.getAllowAiOverride());
        log.setCreatedAt(LocalDateTime.now());
        namingLogService.save(log);
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
