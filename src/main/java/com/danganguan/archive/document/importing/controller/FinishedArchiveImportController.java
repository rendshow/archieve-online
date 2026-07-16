package com.danganguan.archive.document.importing.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.importing.dto.FinishedArchiveChunkUploadResult;
import com.danganguan.archive.document.importing.dto.FinishedArchiveImportCleanupResult;
import com.danganguan.archive.document.importing.dto.FinishedArchiveChunkedCompleteRequest;
import com.danganguan.archive.document.importing.entity.FinishedArchiveImportJob;
import com.danganguan.archive.document.importing.service.FinishedArchiveImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "成品档案导入", description = "已整理档案文件夹或一次压缩包直接导入正式档案库")
@RestController
@RequiredArgsConstructor
public class FinishedArchiveImportController {
    private final FinishedArchiveImportService finishedArchiveImportService;

    @Operation(summary = "创建成品档案导入任务", description = "按馆上传已处理好的 PDF/图片文件夹或一次 ZIP/7Z 压缩包，返回导入任务并在后台异步导入")
    @PostMapping("/api/archive-imports/finished")
    public Result<FinishedArchiveImportJob> importFinishedArchives(@RequestParam Long hallId,
                                                                   @RequestPart("files") List<MultipartFile> files) {
        return Result.ok(finishedArchiveImportService.createImportJob(hallId, files));
    }

    @Operation(summary = "创建成品档案导入任务", description = "按馆上传已处理好的 PDF/图片文件夹或一次 ZIP/7Z 压缩包，返回导入任务并在后台异步导入")
    @PostMapping("/api/archive-imports/finished/jobs")
    public Result<FinishedArchiveImportJob> createImportJob(@RequestParam Long hallId,
                                                            @RequestPart("files") List<MultipartFile> files) {
        return Result.ok(finishedArchiveImportService.createImportJob(hallId, files));
    }

    @Operation(summary = "创建成品档案分片导入任务", description = "创建分片上传会话，随后逐片上传文件，最后调用完成接口合并并进入后台导入队列")
    @PostMapping("/api/archive-imports/finished/chunked/jobs")
    public Result<FinishedArchiveImportJob> createChunkedImportJob(@RequestParam Long hallId) {
        return Result.ok(finishedArchiveImportService.createChunkedImportJob(hallId));
    }

    @Operation(summary = "上传成品档案文件分片", description = "按文件索引和分片索引上传一个文件分片，支持前端失败后重传同一分片")
    @PostMapping("/api/archive-imports/finished/chunked/jobs/{jobId}/chunks")
    public Result<FinishedArchiveChunkUploadResult> uploadChunk(@PathVariable Long jobId,
                                                                @RequestParam Integer fileIndex,
                                                                @RequestParam Integer chunkIndex,
                                                                @RequestParam Integer totalChunks,
                                                                @RequestPart("chunk") MultipartFile chunk) {
        return Result.ok(finishedArchiveImportService.uploadChunk(jobId, fileIndex, chunkIndex, totalChunks, chunk));
    }

    @Operation(summary = "完成成品档案分片导入任务", description = "提交文件清单，后端校验并合并所有分片，然后把导入任务投递到后台队列")
    @PostMapping("/api/archive-imports/finished/chunked/jobs/{jobId}/complete")
    public Result<FinishedArchiveImportJob> completeChunkedImportJob(@PathVariable Long jobId,
                                                                     @RequestBody FinishedArchiveChunkedCompleteRequest request) {
        return Result.ok(finishedArchiveImportService.completeChunkedImportJob(jobId, request));
    }

    @Operation(summary = "查询成品档案导入任务", description = "查询成品档案导入任务状态、总数、已导入数量、跳过数量和错误信息")
    @GetMapping("/api/archive-imports/finished/jobs/{jobId}")
    public Result<FinishedArchiveImportJob> getImportJob(@PathVariable Long jobId) {
        return Result.ok(finishedArchiveImportService.getImportJob(jobId));
    }

    @Operation(summary = "清空已导入旧档案", description = "仅删除成品档案导入产生的 IMP 档案、关联标签、对象存储文件和导入任务；不影响工作区任务及其确认入库档案")
    @DeleteMapping("/api/archive-imports/finished")
    public Result<FinishedArchiveImportCleanupResult> deleteAllImportedArchives() {
        return Result.ok(finishedArchiveImportService.deleteAllImportedArchives());
    }

    @Operation(summary = "清空已导入旧档案数据库记录", description = "仅删除成品档案导入产生的 IMP 档案、关联标签和导入任务；保留对象存储文件，适用于切换到新 MinIO bucket 前的开发环境清理")
    @DeleteMapping("/api/archive-imports/finished/database-records")
    public Result<FinishedArchiveImportCleanupResult> deleteAllImportedArchiveRecords() {
        return Result.ok(finishedArchiveImportService.deleteAllImportedArchiveRecords());
    }
}
