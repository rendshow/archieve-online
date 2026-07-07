package com.danganguan.archive.document.importing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.importing.enums.FinishedArchiveImportJobStatus;

import java.time.LocalDateTime;

@TableName("finished_archive_import_job")
public class FinishedArchiveImportJob {
    private Long id;
    private Long hallId;
    private String batchNo;
    private FinishedArchiveImportJobStatus status;
    private Integer totalCount;
    private Integer importedCount;
    private Integer skippedCount;
    private String skippedPreview;
    private String errorMessage;
    private String sourceRootPath;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public FinishedArchiveImportJobStatus getStatus() { return status; }
    public void setStatus(FinishedArchiveImportJobStatus status) { this.status = status; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getImportedCount() { return importedCount; }
    public void setImportedCount(Integer importedCount) { this.importedCount = importedCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public String getSkippedPreview() { return skippedPreview; }
    public void setSkippedPreview(String skippedPreview) { this.skippedPreview = skippedPreview; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSourceRootPath() { return sourceRootPath; }
    public void setSourceRootPath(String sourceRootPath) { this.sourceRootPath = sourceRootPath; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
