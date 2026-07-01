package com.danganguan.archive.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;
import com.danganguan.archive.task.enums.TaskStatus;
import java.time.LocalDateTime;

@TableName("archive_task")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getNamingSource() { return namingSource; }
    public void setNamingSource(String namingSource) { this.namingSource = namingSource; }
    public String getFolderNameExample() { return folderNameExample; }
    public void setFolderNameExample(String folderNameExample) { this.folderNameExample = folderNameExample; }
    public String getFileNameExample() { return fileNameExample; }
    public void setFileNameExample(String fileNameExample) { this.fileNameExample = fileNameExample; }
    public Boolean getAllowAiOverride() { return allowAiOverride; }
    public void setAllowAiOverride(Boolean allowAiOverride) { this.allowAiOverride = allowAiOverride; }
    public Boolean getEnableScanEnhance() { return enableScanEnhance; }
    public void setEnableScanEnhance(Boolean enableScanEnhance) { this.enableScanEnhance = enableScanEnhance; }
    public PersonSplitStrategy getPersonSplitStrategy() { return personSplitStrategy; }
    public void setPersonSplitStrategy(PersonSplitStrategy personSplitStrategy) { this.personSplitStrategy = personSplitStrategy; }
    public Integer getFixedElementsPerPerson() { return fixedElementsPerPerson; }
    public void setFixedElementsPerPerson(Integer fixedElementsPerPerson) { this.fixedElementsPerPerson = fixedElementsPerPerson; }
    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
