package com.danganguan.archive.file.controller;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadFileStatus;
import com.danganguan.archive.file.service.UploadedFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "原始文件", description = "任务原始上传文件接口")
@RestController
@RequiredArgsConstructor
public class UploadedFileController {
    private final UploadedFileService uploadedFileService;

    @Operation(summary = "上传原始文件", description = "向指定任务上传 PDF、图片或 ZIP 文件，字段名为 files")
    @PostMapping("/api/tasks/{taskId}/files")
    public Result<List<UploadedFile>> upload(@PathVariable Long taskId,
                                             @RequestPart("files") List<MultipartFile> files) {
        return Result.ok(uploadedFileService.upload(taskId, files));
    }

    @Operation(summary = "查询任务上传文件", description = "查询指定任务下的原始上传文件列表")
    @GetMapping("/api/tasks/{taskId}/files")
    public Result<List<UploadedFile>> listByTask(@PathVariable Long taskId) {
        return Result.ok(uploadedFileService.listByTask(taskId));
    }

    @Operation(summary = "删除原始上传文件", description = "软删除单个原始上传文件")
    @DeleteMapping("/api/uploaded-files/{fileId}")
    public Result<Void> delete(@PathVariable Long fileId) {
        UploadedFile file = uploadedFileService.getById(fileId);
        if (file == null) {
            throw new BizException("原始上传文件不存在");
        }
        if (file.getStatus() == UploadFileStatus.QUEUED || file.getStatus() == UploadFileStatus.PROCESSING) {
            throw new BizException("处理中的文件不可删除");
        }
        uploadedFileService.removeById(fileId);
        return Result.ok();
    }
}
