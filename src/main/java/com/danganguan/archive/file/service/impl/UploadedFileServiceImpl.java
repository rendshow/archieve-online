package com.danganguan.archive.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.enums.UploadGroupType;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.mapper.UploadedFileMapper;
import com.danganguan.archive.file.service.UploadedFileService;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.TaskStatus;
import com.danganguan.archive.task.service.ArchiveTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadedFileServiceImpl extends ServiceImpl<UploadedFileMapper, UploadedFile> implements UploadedFileService {
    private final ArchiveTaskService archiveTaskService;
    private final FileStorageService fileStorageService;

    @Override
    public List<UploadedFile> upload(Long taskId, List<MultipartFile> files) {
        ArchiveTask task = archiveTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("上传任务不存在");
        }
        if (files == null || files.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }

        String looseImagesGroupNo = null;
        int looseImageOrder = 1;
        List<UploadedFile> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String ext = extractExt(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
            UploadType uploadType = resolveUploadType(ext);
            if (uploadType != UploadType.ZIP) {
                throw new BizException("当前 MVP 仅支持上传图片压缩包（.zip/.7z）");
            }
            UploadGroupType groupType = resolveGroupType(uploadType);
            String groupNo;
            int groupOrder;
            if (groupType == UploadGroupType.LOOSE_IMAGES) {
                if (looseImagesGroupNo == null) {
                    looseImagesGroupNo = nextGroupNo(task.getId());
                }
                groupNo = looseImagesGroupNo;
                groupOrder = looseImageOrder++;
            } else {
                groupNo = nextGroupNo(task.getId());
                groupOrder = 1;
            }
            savedFiles.add(saveOne(task, file, ext, uploadType, groupType, groupNo, groupOrder));
        }

        task.setStatus(TaskStatus.PENDING_PROCESS);
        task.setUpdatedAt(LocalDateTime.now());
        archiveTaskService.updateById(task);
        return savedFiles;
    }

    @Override
    public List<UploadedFile> listByTask(Long taskId) {
        return lambdaQuery()
                .eq(UploadedFile::getTaskId, taskId)
                .orderByAsc(UploadedFile::getUploadGroupNo)
                .orderByAsc(UploadedFile::getGroupOrder)
                .list();
    }

    private UploadedFile saveOne(ArchiveTask task, MultipartFile file, String ext, UploadType uploadType,
                                 UploadGroupType groupType, String groupNo, int groupOrder) {
        LocalDateTime now = LocalDateTime.now();
        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        StoredFile storedFile = fileStorageService.saveRaw(task.getId(), file, ext);

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setTaskId(task.getId());
        uploadedFile.setHallId(task.getHallId());
        uploadedFile.setOriginalName(originalName);
        uploadedFile.setFileExt(ext);
        uploadedFile.setMediaType(file.getContentType());
        uploadedFile.setUploadGroupNo(groupNo);
        uploadedFile.setGroupType(groupType);
        uploadedFile.setGroupOrder(groupOrder);
        uploadedFile.setFileSize(storedFile.size());
        uploadedFile.setFileSha256(storedFile.sha256());
        uploadedFile.setStoragePath(storedFile.relativePath());
        uploadedFile.setUploadType(uploadType);
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
            case "zip", "7z" -> UploadType.ZIP;
            default -> UploadType.UNKNOWN;
        };
    }

    private UploadGroupType resolveGroupType(UploadType uploadType) {
        return switch (uploadType) {
            case IMAGE -> UploadGroupType.LOOSE_IMAGES;
            case ZIP -> UploadGroupType.ZIP;
            case PDF, UNKNOWN -> UploadGroupType.SINGLE_FILE;
        };
    }

    private String nextGroupNo(Long taskId) {
        return "UG" + taskId + "-" + UUID.randomUUID();
    }
}
