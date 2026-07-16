package com.danganguan.archive.document.fact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("archive_extracted_fact")
public class ArchiveExtractedFact {
    private Long id;
    private Long archiveDocumentId;
    private Long archiveDocumentPageId;
    private ArchiveFactType factType;
    private String factKey;
    private String factValue;
    private String normalizedValue;
    private BigDecimal confidence;
    private String evidenceText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArchiveDocumentId() { return archiveDocumentId; }
    public void setArchiveDocumentId(Long archiveDocumentId) { this.archiveDocumentId = archiveDocumentId; }
    public Long getArchiveDocumentPageId() { return archiveDocumentPageId; }
    public void setArchiveDocumentPageId(Long archiveDocumentPageId) { this.archiveDocumentPageId = archiveDocumentPageId; }
    public ArchiveFactType getFactType() { return factType; }
    public void setFactType(ArchiveFactType factType) { this.factType = factType; }
    public String getFactKey() { return factKey; }
    public void setFactKey(String factKey) { this.factKey = factKey; }
    public String getFactValue() { return factValue; }
    public void setFactValue(String factValue) { this.factValue = factValue; }
    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String normalizedValue) { this.normalizedValue = normalizedValue; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
