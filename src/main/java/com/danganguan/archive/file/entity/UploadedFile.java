package com.danganguan.archive.file.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.enums.UploadGroupType;
import com.danganguan.archive.file.enums.UploadType;

import java.time.LocalDateTime;

@TableName("uploaded_file")
@Getter
@Setter
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


















}
