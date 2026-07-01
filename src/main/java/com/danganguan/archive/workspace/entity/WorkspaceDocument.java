package com.danganguan.archive.workspace.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.workspace.enums.WorkspaceDocumentStatus;

import java.time.LocalDateTime;

@TableName("workspace_document")
public class WorkspaceDocument {
    private Long id;
    private Long taskId;
    private Long hallId;
    private Long sourceFileId;
    private String suggestedName;
    private String finalName;
    private String folderName;
    private OutputFormat outputFormat;
    private String storagePath;
    private Integer pageCount;
    private String aiSummary;
    private String namingReason;
    private WorkspaceDocumentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public Long getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }
    public String getSuggestedName() { return suggestedName; }
    public void setSuggestedName(String suggestedName) { this.suggestedName = suggestedName; }
    public String getFinalName() { return finalName; }
    public void setFinalName(String finalName) { this.finalName = finalName; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getNamingReason() { return namingReason; }
    public void setNamingReason(String namingReason) { this.namingReason = namingReason; }
    public WorkspaceDocumentStatus getStatus() { return status; }
    public void setStatus(WorkspaceDocumentStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
