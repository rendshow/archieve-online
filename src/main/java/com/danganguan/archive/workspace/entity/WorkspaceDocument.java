package com.danganguan.archive.workspace.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.workspace.enums.WorkspaceDocumentStatus;

import java.time.LocalDateTime;

@TableName("workspace_document")
@Getter
@Setter
public class WorkspaceDocument {
    private Long id;
    private Long taskId;
    private Long hallId;
    private Long sourceFileId;
    private String suggestedName;
    private String finalName;
    private String folderName;
    private OutputFormat outputFormat;
    private String storagePath;
    private Integer pageCount;
    private String aiSummary;
    private String ocrText;
    private String namingReason;
    private WorkspaceDocumentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

















}
