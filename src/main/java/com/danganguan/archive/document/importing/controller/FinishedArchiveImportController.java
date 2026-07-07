package com.danganguan.archive.document.importing.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.importing.entity.FinishedArchiveImportJob;
import com.danganguan.archive.document.importing.service.FinishedArchiveImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "查询成品档案导入任务", description = "查询成品档案导入任务状态、总数、已导入数量、跳过数量和错误信息")
    @GetMapping("/api/archive-imports/finished/jobs/{jobId}")
    public Result<FinishedArchiveImportJob> getImportJob(@PathVariable Long jobId) {
        return Result.ok(finishedArchiveImportService.getImportJob(jobId));
    }
}
