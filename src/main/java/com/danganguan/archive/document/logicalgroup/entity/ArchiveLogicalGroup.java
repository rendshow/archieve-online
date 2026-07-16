package com.danganguan.archive.document.logicalgroup.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupConfidence;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupType;

import java.time.LocalDateTime;

@TableName("archive_logical_group")
public class ArchiveLogicalGroup {
    private Long id;
    private Long hallId;
    private String folderPath;
    private String groupKey;
    private ArchiveLogicalGroupType groupType;
    private String title;
    private String personName;
    private String archiveNo;
    private ArchiveLogicalGroupConfidence confidence;
    private String groupingRule;
    private Boolean requiresReview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public ArchiveLogicalGroupType getGroupType() { return groupType; }
    public void setGroupType(ArchiveLogicalGroupType groupType) { this.groupType = groupType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }
    public String getArchiveNo() { return archiveNo; }
    public void setArchiveNo(String archiveNo) { this.archiveNo = archiveNo; }
    public ArchiveLogicalGroupConfidence getConfidence() { return confidence; }
    public void setConfidence(ArchiveLogicalGroupConfidence confidence) { this.confidence = confidence; }
    public String getGroupingRule() { return groupingRule; }
    public void setGroupingRule(String groupingRule) { this.groupingRule = groupingRule; }
    public Boolean getRequiresReview() { return requiresReview; }
    public void setRequiresReview(Boolean requiresReview) { this.requiresReview = requiresReview; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
