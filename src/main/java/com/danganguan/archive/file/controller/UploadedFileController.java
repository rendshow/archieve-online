package com.danganguan.archive.file.controller;

import com.danganguan.archive.common.response.ApiResponse;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.service.UploadedFileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class UploadedFileController {
    private final UploadedFileService uploadedFileService;

    public UploadedFileController(UploadedFileService uploadedFileService) {
        this.uploadedFileService = uploadedFileService;
    }

    @PostMapping("/api/tasks/{taskId}/files")
    public ApiResponse<List<UploadedFile>> upload(@PathVariable Long taskId,
                                                  @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(uploadedFileService.upload(taskId, files));
    }

    @GetMapping("/api/tasks/{taskId}/files")
    public ApiResponse<List<UploadedFile>> listByTask(@PathVariable Long taskId) {
        return ApiResponse.ok(uploadedFileService.listByTask(taskId));
    }

    @DeleteMapping("/api/uploaded-files/{fileId}")
    public ApiResponse<Void> delete(@PathVariable Long fileId) {
        uploadedFileService.removeById(fileId);
        return ApiResponse.ok();
    }
}
