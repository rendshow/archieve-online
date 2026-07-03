package com.danganguan.archive.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.dto.ArchiveDocumentQuery;
import com.danganguan.archive.document.dto.UpdateArchiveDocumentNameRequest;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "正式档案", description = "正式档案入库、查询、修改和删除接口")
@RestController
@RequiredArgsConstructor
public class ArchiveDocumentController {
    private final ArchiveDocumentService archiveDocumentService;

    @Operation(summary = "确认工作区档案入库", description = "将工作区档案确认为正式档案，并复制标签、更新任务状态")
    @PostMapping("/api/workspace-documents/{id}/approve")
    public Result<ArchiveDocument> approve(@PathVariable Long id) {
        return Result.ok(archiveDocumentService.approveWorkspaceDocument(id));
    }

    @Operation(summary = "分页查询正式档案", description = "按馆、任务、关键词、文件夹、标签分页查询正式档案")
    @GetMapping("/api/archive-documents")
    public Result<IPage<ArchiveDocument>> page(ArchiveDocumentQuery query) {
        return Result.ok(archiveDocumentService.pageDocuments(query));
    }

    @Operation(summary = "查询正式档案详情", description = "根据正式档案 ID 查询详情")
    @GetMapping("/api/archive-documents/{id}")
    public Result<ArchiveDocument> detail(@PathVariable Long id) {
        return Result.ok(archiveDocumentService.getById(id));
    }

    @Operation(summary = "修改正式档案名称", description = "修改正式档案标题")
    @PutMapping("/api/archive-documents/{id}/name")
    public Result<ArchiveDocument> updateName(@PathVariable Long id,
                                              @RequestBody UpdateArchiveDocumentNameRequest request) {
        return Result.ok(archiveDocumentService.updateName(id, request));
    }

    @Operation(summary = "删除正式档案", description = "软删除单个正式档案")
    @DeleteMapping("/api/archive-documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        archiveDocumentService.deleteDocument(id);
        return Result.ok();
    }
}
