package com.danganguan.archive.document.indexing.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.indexing.enums.ArchiveTextIndexJobStatus;
import com.danganguan.archive.document.indexing.enums.ArchiveTextIndexMode;

import java.time.LocalDateTime;

@TableName("archive_text_index_job")
@Getter
@Setter
public class ArchiveTextIndexJob {
    private Long id;
    private Long hallId;
    private ArchiveTextIndexMode mode;
    private String documentIdsJson;
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
















}
