package com.danganguan.archive.tag.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.tag.enums.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("document_tag")
@Getter
@Setter
public class DocumentTag {
    private Long id;
    private DocumentType documentType;
    private Long documentId;
    private Long tagId;
    private BigDecimal confidence;
    private LocalDateTime createdAt;






}
