package com.danganguan.archive.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.dto.ArchiveDocumentQuery;
import com.danganguan.archive.document.dto.UpdateArchiveDocumentNameRequest;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.mapper.ArchiveDocumentMapper;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import com.danganguan.archive.tag.entity.DocumentTag;
import com.danganguan.archive.tag.enums.DocumentType;
import com.danganguan.archive.tag.service.DocumentTagService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.service.ArchiveTaskService;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.enums.WorkspaceDocumentStatus;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ArchiveDocumentServiceImpl extends ServiceImpl<ArchiveDocumentMapper, ArchiveDocument>
        implements ArchiveDocumentService {
    private final WorkspaceDocumentService workspaceDocumentService;
    private final ArchiveTaskService archiveTaskService;
    private final DocumentTagService documentTagService;

    public ArchiveDocumentServiceImpl(WorkspaceDocumentService workspaceDocumentService,
                                      ArchiveTaskService archiveTaskService,
                                      DocumentTagService documentTagService) {
        this.workspaceDocumentService = workspaceDocumentService;
        this.archiveTaskService = archiveTaskService;
        this.documentTagService = documentTagService;
    }

    @Override
    @Transactional
    public ArchiveDocument approveWorkspaceDocument(Long workspaceDocumentId) {
        WorkspaceDocument workspaceDocument = workspaceDocumentService.getById(workspaceDocumentId);
        if (workspaceDocument == null) {
            throw new BizException("工作区文件不存在");
        }
        if (workspaceDocument.getStatus() == WorkspaceDocumentStatus.APPROVED) {
            ArchiveDocument existing = lambdaQuery()
                    .eq(ArchiveDocument::getWorkspaceDocumentId, workspaceDocumentId)
                    .one();
            if (existing != null) {
                return existing;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ArchiveDocument archiveDocument = new ArchiveDocument();
        archiveDocument.setHallId(workspaceDocument.getHallId());
        archiveDocument.setTaskId(workspaceDocument.getTaskId());
        archiveDocument.setWorkspaceDocumentId(workspaceDocument.getId());
        archiveDocument.setArchiveNo(buildArchiveNo(workspaceDocument, now));
        archiveDocument.setTitle(workspaceDocument.getFinalName());
        archiveDocument.setFolderName(workspaceDocument.getFolderName());
        archiveDocument.setFileFormat(workspaceDocument.getOutputFormat());
        archiveDocument.setStoragePath(workspaceDocument.getStoragePath());
        archiveDocument.setPageCount(workspaceDocument.getPageCount());
        archiveDocument.setAiSummary(workspaceDocument.getAiSummary());
        archiveDocument.setOcrText(null);
        archiveDocument.setStatus(ArchiveDocumentStatus.ACTIVE);
        archiveDocument.setArchivedAt(now);
        archiveDocument.setCreatedAt(now);
        archiveDocument.setUpdatedAt(now);
        archiveDocument.setDeleted(0);
        save(archiveDocument);

        copyWorkspaceTags(workspaceDocument.getId(), archiveDocument.getId());

        workspaceDocument.setStatus(WorkspaceDocumentStatus.APPROVED);
        workspaceDocument.setUpdatedAt(now);
        workspaceDocumentService.updateById(workspaceDocument);

        ArchiveTask task = archiveTaskService.getById(workspaceDocument.getTaskId());
        if (task != null) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setUpdatedAt(now);
            archiveTaskService.updateById(task);
        }
        return archiveDocument;
    }

    @Override
    public IPage<ArchiveDocument> pageDocuments(ArchiveDocumentQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? 20 : query.size();
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(query.hallId() != null, ArchiveDocument::getHallId, query.hallId())
                .eq(query.taskId() != null, ArchiveDocument::getTaskId, query.taskId())
                .like(query.keyword() != null && !query.keyword().isBlank(), ArchiveDocument::getTitle, query.keyword())
                .like(query.folderName() != null && !query.folderName().isBlank(), ArchiveDocument::getFolderName, query.folderName())
                .orderByDesc(ArchiveDocument::getArchivedAt);

        if (query.tagId() != null) {
            List<Long> documentIds = documentTagService.lambdaQuery()
                    .eq(DocumentTag::getDocumentType, DocumentType.ARCHIVE)
                    .eq(DocumentTag::getTagId, query.tagId())
                    .list()
                    .stream()
                    .map(DocumentTag::getDocumentId)
                    .toList();
            if (documentIds.isEmpty()) {
                return Page.of(page, size);
            }
            wrapper.in(ArchiveDocument::getId, documentIds);
        }

        return page(Page.of(page, size), wrapper);
    }

    @Override
    public ArchiveDocument updateName(Long id, UpdateArchiveDocumentNameRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BizException("档案名称不能为空");
        }
        ArchiveDocument document = getById(id);
        if (document == null) {
            throw new BizException("正式档案不存在");
        }
        document.setTitle(request.title().trim());
        document.setUpdatedAt(LocalDateTime.now());
        updateById(document);
        return document;
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        ArchiveDocument document = getById(id);
        if (document == null) {
            return;
        }
        document.setStatus(ArchiveDocumentStatus.DELETED);
        document.setUpdatedAt(LocalDateTime.now());
        updateById(document);
        removeById(id);
    }

    private String buildArchiveNo(WorkspaceDocument workspaceDocument, LocalDateTime now) {
        return "ARCH" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + "-" + workspaceDocument.getId();
    }

    private void copyWorkspaceTags(Long workspaceDocumentId, Long archiveDocumentId) {
        List<DocumentTag> workspaceTags = documentTagService.lambdaQuery()
                .eq(DocumentTag::getDocumentType, DocumentType.WORKSPACE)
                .eq(DocumentTag::getDocumentId, workspaceDocumentId)
                .list();
        for (DocumentTag workspaceTag : workspaceTags) {
            DocumentTag archiveTag = new DocumentTag();
            archiveTag.setDocumentType(DocumentType.ARCHIVE);
            archiveTag.setDocumentId(archiveDocumentId);
            archiveTag.setTagId(workspaceTag.getTagId());
            archiveTag.setConfidence(workspaceTag.getConfidence() == null ? BigDecimal.ONE : workspaceTag.getConfidence());
            archiveTag.setCreatedAt(LocalDateTime.now());
            documentTagService.save(archiveTag);
        }
    }
}
