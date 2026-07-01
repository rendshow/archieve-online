package com.danganguan.archive.file.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.mapper.UploadedFileMapper;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.service.ArchiveTaskService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UploadedFileService extends ServiceImpl<UploadedFileMapper, UploadedFile> {
    private final ArchiveTaskService archiveTaskService;
    private final FileStorageService fileStorageService;

    public UploadedFileService(ArchiveTaskService archiveTaskService, FileStorageService fileStorageService) {
        this.archiveTaskService = archiveTaskService;
        this.fileStorageService = fileStorageService;
    }

    public List<UploadedFile> upload(Long taskId, List<MultipartFile> files) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        if (files == null || files.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }

        List<UploadedFile> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            savedFiles.add(saveOne(task, file));
        }

        task.setStatus(TaskStatus.PENDING_PROCESS);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);
        return savedFiles;
    }

    public List<UploadedFile> listByTask(Long taskId) {
        return lambdaQuery().eq(UploadedFile::getTaskId, taskId).orderByDesc(UploadedFile::getCreatedAt).list();
    }

    private UploadedFile saveOne(ArchiveTask task, MultipartFile file) {
        LocalDateTime now = LocalDateTime.now();
        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String ext = extractExt(originalName);
        StoredFile storedFile = fileStorageService.saveRaw(task.getId(), file, ext);

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setTaskId(task.getId());
        uploadedFile.setHallId(task.getHallId());
        uploadedFile.setOriginalName(originalName);
        uploadedFile.setFileExt(ext);
        uploadedFile.setMediaType(file.getContentType());
        uploadedFile.setFileSize(storedFile.size());
        uploadedFile.setFileSha256(storedFile.sha256());
        uploadedFile.setStoragePath(storedFile.relativePath());
        uploadedFile.setUploadType(resolveUploadType(ext));
        uploadedFile.setStatus(UploadFileStatus.SAVED);
        uploadedFile.setCreatedAt(now);
        uploadedFile.setUpdatedAt(now);
        uploadedFile.setDeleted(0);
        save(uploadedFile);
        return uploadedFile;
    }

    private String extractExt(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private UploadType resolveUploadType(String ext) {
        return switch (ext) {
            case "pdf" -> UploadType.PDF;
            case "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff" -> UploadType.IMAGE;
            case "zip" -> UploadType.ZIP;
            default -> UploadType.UNKNOWN;
        };
    }
}
