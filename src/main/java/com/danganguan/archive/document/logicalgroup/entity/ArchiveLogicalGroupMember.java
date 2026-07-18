package com.danganguan.archive.document.logicalgroup.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("archive_logical_group_member")
@Getter
@Setter
public class ArchiveLogicalGroupMember {
    private Long id;
    private Long groupId;
    private Long archiveDocumentId;
    private Integer memberOrder;
    private LocalDateTime createdAt;





}
