package com.danganguan.archive.tag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.tag.enums.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("document_tag")
public class DocumentTag {
    private Long id;
    private DocumentType documentType;
    private Long documentId;
    private Long tagId;
    private BigDecimal confidence;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
