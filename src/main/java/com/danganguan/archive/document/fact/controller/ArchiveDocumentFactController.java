package com.danganguan.archive.document.fact.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.dto.ArchiveFactSearchRequest;
import com.danganguan.archive.document.fact.service.ArchiveDocumentFactQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "档案事实证据", description = "从页级 OCR 中抽取的只读事实及其原始证据")
@RestController
@RequestMapping("/api/archive-document-facts")
@RequiredArgsConstructor
public class ArchiveDocumentFactController {
    private final ArchiveDocumentFactQueryService archiveDocumentFactQueryService;

    @Operation(summary = "查询范围内档案事实", description = "支持按馆区、目录、事实类型和值查询，结果始终携带档案、页码与 OCR 证据")
    @GetMapping
    public Result<List<ArchiveFactEvidence>> search(ArchiveFactSearchRequest request) {
        return Result.ok(archiveDocumentFactQueryService.search(request));
    }

    @Operation(summary = "查看单个档案的页级事实", description = "用于核查姓名、学号、材料类型、成绩和学位日期的来源页")
    @GetMapping("/documents/{archiveDocumentId}")
    public Result<List<ArchiveFactEvidence>> listByDocumentId(@PathVariable Long archiveDocumentId) {
        return Result.ok(archiveDocumentFactQueryService.listByDocumentId(archiveDocumentId));
    }
}
