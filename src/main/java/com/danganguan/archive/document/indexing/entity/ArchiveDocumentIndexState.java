package com.danganguan.archive.document.indexing.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.danganguan.archive.document.indexing.enums.ArchiveDocumentIndexStatus;

import java.time.LocalDateTime;

@TableName("archive_document_index_state")
@Getter
@Setter
public class ArchiveDocumentIndexState {
    @TableId(value = "document_id", type = IdType.INPUT)
    private Long documentId;
    private ArchiveDocumentIndexStatus status;
    private String indexVersion;
    private Integer attemptCount;
    private String lastError;
    private LocalDateTime indexedAt;
    private LocalDateTime updatedAt;







}
