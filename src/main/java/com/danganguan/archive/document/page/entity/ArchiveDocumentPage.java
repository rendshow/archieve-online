package com.danganguan.archive.document.page.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("archive_document_page")
@Getter
@Setter
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









}
