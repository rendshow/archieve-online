package com.danganguan.archive.workspace.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("naming_log")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }
    public Long getWorkspaceDocumentId() { return workspaceDocumentId; }
    public void setWorkspaceDocumentId(Long workspaceDocumentId) { this.workspaceDocumentId = workspaceDocumentId; }
    public String getUserReference() { return userReference; }
    public void setUserReference(String userReference) { this.userReference = userReference; }
    public String getHistoryReference() { return historyReference; }
    public void setHistoryReference(String historyReference) { this.historyReference = historyReference; }
    public String getAiSuggestedName() { return aiSuggestedName; }
    public void setAiSuggestedName(String aiSuggestedName) { this.aiSuggestedName = aiSuggestedName; }
    public String getFinalName() { return finalName; }
    public void setFinalName(String finalName) { this.finalName = finalName; }
    public String getNamingReason() { return namingReason; }
    public void setNamingReason(String namingReason) { this.namingReason = namingReason; }
    public Boolean getAllowAiOverride() { return allowAiOverride; }
    public void setAllowAiOverride(Boolean allowAiOverride) { this.allowAiOverride = allowAiOverride; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
