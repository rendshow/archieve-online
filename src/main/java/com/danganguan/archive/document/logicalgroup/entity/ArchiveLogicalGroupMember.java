package com.danganguan.archive.document.logicalgroup.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("archive_logical_group_member")
public class ArchiveLogicalGroupMember {
    private Long id;
    private Long groupId;
    private Long archiveDocumentId;
    private Integer memberOrder;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getArchiveDocumentId() { return archiveDocumentId; }
    public void setArchiveDocumentId(Long archiveDocumentId) { this.archiveDocumentId = archiveDocumentId; }
    public Integer getMemberOrder() { return memberOrder; }
    public void setMemberOrder(Integer memberOrder) { this.memberOrder = memberOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
