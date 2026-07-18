package com.danganguan.archive.workspace.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("naming_log")
@Getter
@Setter
public class NamingLog {
    private Long id;
    private Long taskId;
    private Long sourceFileId;
    private Long workspaceDocumentId;
    private String userReference;
    private String historyReference;
    private String aiSuggestedName;
    private String finalName;
    private String namingReason;
    private Boolean allowAiOverride;
    private LocalDateTime createdAt;











}
