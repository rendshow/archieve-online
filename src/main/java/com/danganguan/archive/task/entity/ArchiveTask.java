package com.danganguan.archive.task.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;
import com.danganguan.archive.task.enums.TaskStatus;
import java.time.LocalDateTime;

@TableName("archive_task")
@Getter
@Setter
public class ArchiveTask {
    private Long id;
    private String taskNo;
    private Long hallId;
    private String taskName;
    private String namingSource;
    private String folderNameExample;
    private String fileNameExample;
    private Boolean allowAiOverride;
    private Boolean enableScanEnhance;
    @TableField("convert_strategy")
    private PersonSplitStrategy personSplitStrategy;
    @TableField("fixed_split_count")
    private Integer fixedElementsPerPerson;
    private OutputFormat outputFormat;
    private TaskStatus status;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;


















}
