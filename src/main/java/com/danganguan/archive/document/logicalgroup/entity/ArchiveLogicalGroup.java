package com.danganguan.archive.document.logicalgroup.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupConfidence;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupType;

import java.time.LocalDateTime;

@TableName("archive_logical_group")
@Getter
@Setter
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














}
