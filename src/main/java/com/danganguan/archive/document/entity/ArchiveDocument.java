package com.danganguan.archive.document.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.task.enums.OutputFormat;

import java.time.LocalDateTime;

@TableName("archive_document")
public class ArchiveDocument {
    private Long id;
    private Long hallId;
    private Long taskId;
    private Long workspaceDocumentId;
    private String archiveNo;
    private String title;
    private String folderName;
    private OutputFormat fileFormat;
    private String storagePath;
    private Integer pageCount;
    private String aiSummary;
    private String ocrText;
    private ArchiveDocumentStatus status;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getWorkspaceDocumentId() { return workspaceDocumentId; }
    public void setWorkspaceDocumentId(Long workspaceDocumentId) { this.workspaceDocumentId = workspaceDocumentId; }
    public String getArchiveNo() { return archiveNo; }
    public void setArchiveNo(String archiveNo) { this.archiveNo = archiveNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public OutputFormat getFileFormat() { return fileFormat; }
    public void setFileFormat(OutputFormat fileFormat) { this.fileFormat = fileFormat; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public ArchiveDocumentStatus getStatus() { return status; }
    public void setStatus(ArchiveDocumentStatus status) { this.status = status; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
