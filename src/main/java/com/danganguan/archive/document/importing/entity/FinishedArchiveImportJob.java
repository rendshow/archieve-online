package com.danganguan.archive.document.importing.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.importing.enums.FinishedArchiveImportJobStatus;

import java.time.LocalDateTime;

@TableName("finished_archive_import_job")
@Getter
@Setter
public class FinishedArchiveImportJob {
    private Long id;
    private Long hallId;
    private String batchNo;
    private FinishedArchiveImportJobStatus status;
    private Integer totalCount;
    private Integer importedCount;
    private Integer skippedCount;
    private String skippedPreview;
    private String errorMessage;
    private String sourceRootPath;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;














}
