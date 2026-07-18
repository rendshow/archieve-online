package com.danganguan.archive.document.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.task.enums.OutputFormat;

import java.time.LocalDateTime;

@TableName("archive_document")
@Getter
@Setter
public class ArchiveDocument {
    private Long id;
    private Long hallId;
    private Long taskId;
    private Long workspaceDocumentId;
    private String archiveNo;
    private String title;
    private String folderName;
    private String folderPath;
    private OutputFormat fileFormat;
    private String storagePath;
    private Integer pageCount;
    private String aiSummary;
    private String ocrText;
    private ArchiveDocumentStatus status;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;


















}
