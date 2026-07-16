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
import com.danganguan.archive.document.logicalgroup.event.ArchiveLogicalGroupRefreshRequested;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentServiceImpl extends ServiceImpl<ArchiveDocumentMapper, ArchiveDocument>
        implements ArchiveDocumentService {
    private final WorkspaceDocumentService workspaceDocumentService;
    private final ArchiveTaskService archiveTaskService;
    private final DocumentTagService documentTagService;
    private final ApplicationEventPublisher applicationEventPublisher;

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
        archiveDocument.setFolderPath(workspaceDocument.getFolderName());
        archiveDocument.setFileFormat(workspaceDocument.getOutputFormat());
        archiveDocument.setStoragePath(workspaceDocument.getStoragePath());
        archiveDocument.setPageCount(workspaceDocument.getPageCount());
        archiveDocument.setAiSummary(workspaceDocument.getAiSummary());
        archiveDocument.setOcrText(workspaceDocument.getOcrText());
        archiveDocument.setStatus(ArchiveDocumentStatus.ACTIVE);
        archiveDocument.setArchivedAt(now);
        archiveDocument.setCreatedAt(now);
        archiveDocument.setUpdatedAt(now);
        archiveDocument.setDeleted(0);
        save(archiveDocument);
        requestLogicalGroupRefresh(archiveDocument, "工作区档案审核入库");

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
                .like(query.folderName() != null && !query.folderName().isBlank(), ArchiveDocument::getFolderName, query.folderName())
                .like(query.folderPath() != null && !query.folderPath().isBlank(), ArchiveDocument::getFolderPath, query.folderPath())
                .orderByDesc(ArchiveDocument::getArchivedAt);

        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            wrapper.and(inner -> inner
                    .like(ArchiveDocument::getTitle, keyword)
                    .or()
                    .like(ArchiveDocument::getFolderName, keyword)
                    .or()
                    .like(ArchiveDocument::getFolderPath, keyword)
                    .or()
                    .like(ArchiveDocument::getAiSummary, keyword)
                    .or()
                    .like(ArchiveDocument::getOcrText, keyword));
        }

        if (query.tagId() != null || (query.tagName() != null && !query.tagName().isBlank())) {
            List<Long> documentIds = documentTagService.findDocumentIds(DocumentType.ARCHIVE, query.tagId(), query.tagName());
            if (documentIds.isEmpty()) {
                return Page.of(page, size);
            }
            wrapper.in(ArchiveDocument::getId, documentIds);
        }

        return page(Page.of(page, size), wrapper);
    }

    @Override
    @Transactional
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
        requestLogicalGroupRefresh(document, "正式档案名称已修改");
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
        requestLogicalGroupRefresh(document, "正式档案已删除");
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

    private void requestLogicalGroupRefresh(ArchiveDocument document, String reason) {
        applicationEventPublisher.publishEvent(new ArchiveLogicalGroupRefreshRequested(
                document.getHallId(),
                Set.of(folderPath(document)),
                reason
        ));
    }

    private String folderPath(ArchiveDocument document) {
        String folderPath = document.getFolderPath();
        return folderPath == null || folderPath.isBlank() ? document.getFolderName() : folderPath;
    }
}
