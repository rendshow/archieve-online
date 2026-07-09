package com.danganguan.archive.document.indexing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.indexing.enums.ArchiveTextIndexJobStatus;

import java.time.LocalDateTime;

@TableName("archive_text_index_job")
public class ArchiveTextIndexJob {
    private Long id;
    private Long hallId;
    private ArchiveTextIndexJobStatus status;
    private Integer batchSize;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failedCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public ArchiveTextIndexJobStatus getStatus() { return status; }
    public void setStatus(ArchiveTextIndexJobStatus status) { this.status = status; }
    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getProcessedCount() { return processedCount; }
    public void setProcessedCount(Integer processedCount) { this.processedCount = processedCount; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
