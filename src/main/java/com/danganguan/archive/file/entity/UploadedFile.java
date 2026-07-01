package com.danganguan.archive.file.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.enums.UploadGroupType;
import com.danganguan.archive.file.enums.UploadType;

import java.time.LocalDateTime;

@TableName("uploaded_file")
public class UploadedFile {
    private Long id;
    private Long taskId;
    private Long hallId;
    private String originalName;
    private String fileExt;
    private String mediaType;
    private String uploadGroupNo;
    private UploadGroupType groupType;
    private Integer groupOrder;
    private Long fileSize;
    private String fileSha256;
    private String storagePath;
    private UploadType uploadType;
    private UploadFileStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getFileExt() { return fileExt; }
    public void setFileExt(String fileExt) { this.fileExt = fileExt; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getUploadGroupNo() { return uploadGroupNo; }
    public void setUploadGroupNo(String uploadGroupNo) { this.uploadGroupNo = uploadGroupNo; }
    public UploadGroupType getGroupType() { return groupType; }
    public void setGroupType(UploadGroupType groupType) { this.groupType = groupType; }
    public Integer getGroupOrder() { return groupOrder; }
    public void setGroupOrder(Integer groupOrder) { this.groupOrder = groupOrder; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public UploadType getUploadType() { return uploadType; }
    public void setUploadType(UploadType uploadType) { this.uploadType = uploadType; }
    public UploadFileStatus getStatus() { return status; }
    public void setStatus(UploadFileStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
