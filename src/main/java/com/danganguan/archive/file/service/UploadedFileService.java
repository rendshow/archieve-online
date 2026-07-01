package com.danganguan.archive.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.file.entity.UploadedFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UploadedFileService extends IService<UploadedFile> {
    List<UploadedFile> upload(Long taskId, List<MultipartFile> files);

    List<UploadedFile> listByTask(Long taskId);
}
