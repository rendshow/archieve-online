package com.danganguan.archive.document.legacygrouping.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreview;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreviewRequest;
import com.danganguan.archive.document.legacygrouping.service.LegacyArchiveGroupingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "旧档案归组预览", description = "在受限本地目录内按文件夹和文件名生成只读人员档案归组候选")
@RestController
@RequiredArgsConstructor
public class LegacyArchiveGroupingController {
    private final LegacyArchiveGroupingService legacyArchiveGroupingService;

    @Operation(summary = "预览旧档案归组", description = "只读取配置根目录下一个文件夹的直接 PDF/图片文件，不跨文件夹、不上传、不修改正式档案")
    @PostMapping("/api/legacy-archive-groupings/preview")
    public Result<LegacyArchiveGroupingPreview> preview(@RequestBody(required = false) LegacyArchiveGroupingPreviewRequest request) {
        return Result.ok(legacyArchiveGroupingService.preview(request));
    }
}
