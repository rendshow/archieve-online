package com.danganguan.archive.document.fact.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("archive_extracted_fact")
@Getter
@Setter
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











}
