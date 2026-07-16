package com.danganguan.archive.document.page.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("archive_document_page")
public class ArchiveDocumentPage {
    private Long id;
    private Long archiveDocumentId;
    private Integer pageNo;
    private String ocrText;
    private BigDecimal ocrConfidence;
    private String ocrEngine;
    private String ocrReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArchiveDocumentId() { return archiveDocumentId; }
    public void setArchiveDocumentId(Long archiveDocumentId) { this.archiveDocumentId = archiveDocumentId; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public BigDecimal getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(BigDecimal ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    public String getOcrEngine() { return ocrEngine; }
    public void setOcrEngine(String ocrEngine) { this.ocrEngine = ocrEngine; }
    public String getOcrReason() { return ocrReason; }
    public void setOcrReason(String ocrReason) { this.ocrReason = ocrReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
