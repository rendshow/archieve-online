package com.danganguan.archive.document.importing.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.importing.dto.FinishedArchiveImportResult;
import com.danganguan.archive.document.importing.service.FinishedArchiveImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "导入成品档案", description = "按馆导入已处理好的 PDF/图片文件；支持文件夹上传相对路径，或一次 ZIP/7Z 压缩包")
    @PostMapping("/api/archive-imports/finished")
    public Result<FinishedArchiveImportResult> importFinishedArchives(@RequestParam Long hallId,
                                                                      @RequestPart("files") List<MultipartFile> files) {
        return Result.ok(finishedArchiveImportService.importFinishedArchives(hallId, files));
    }
}
