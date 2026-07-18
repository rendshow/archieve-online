package com.danganguan.archive.document.indexing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.danganguan.archive.document.indexing.enums.ArchiveDocumentIndexStatus;

import java.time.LocalDateTime;

@TableName("archive_document_index_state")
public class ArchiveDocumentIndexState {
    @TableId(value = "document_id", type = IdType.INPUT)
    private Long documentId;
    private ArchiveDocumentIndexStatus status;
    private String indexVersion;
    private Integer attemptCount;
    private String lastError;
    private LocalDateTime indexedAt;
    private LocalDateTime updatedAt;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public ArchiveDocumentIndexStatus getStatus() { return status; }
    public void setStatus(ArchiveDocumentIndexStatus status) { this.status = status; }
    public String getIndexVersion() { return indexVersion; }
    public void setIndexVersion(String indexVersion) { this.indexVersion = indexVersion; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
