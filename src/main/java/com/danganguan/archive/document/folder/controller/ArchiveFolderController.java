package com.danganguan.archive.document.folder.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.folder.dto.ArchiveFolderChildren;
import com.danganguan.archive.document.folder.dto.ArchiveFolderNode;
import com.danganguan.archive.document.folder.dto.MoveArchiveDocumentRequest;
import com.danganguan.archive.document.folder.dto.MoveArchiveFolderRequest;
import com.danganguan.archive.document.folder.dto.MoveArchiveFolderResult;
import com.danganguan.archive.document.folder.service.ArchiveFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "正式档案目录", description = "正式档案库在线文件夹浏览接口")
@RestController
@RequiredArgsConstructor
public class ArchiveFolderController {
    private final ArchiveFolderService archiveFolderService;

    @Operation(summary = "查询正式档案目录树", description = "按馆查询正式档案的完整文件夹树")
    @GetMapping("/api/archive-folders/tree")
    public Result<List<ArchiveFolderNode>> tree(@RequestParam(required = false) Long hallId) {
        return Result.ok(archiveFolderService.tree(hallId));
    }

    @Operation(summary = "查询正式档案目录子项", description = "按馆和目录路径查询直接子文件夹以及当前目录下的档案")
    @GetMapping("/api/archive-folders/children")
    public Result<ArchiveFolderChildren> children(@RequestParam(required = false) Long hallId,
                                                  @RequestParam(required = false) String folderPath) {
        return Result.ok(archiveFolderService.children(hallId, folderPath));
    }

    @Operation(summary = "移动正式档案", description = "将单个正式档案移动到目标目录，仅更新目录元数据，不移动对象存储文件")
    @PatchMapping("/api/archive-documents/{id}/folder")
    public Result<ArchiveDocument> moveDocument(@PathVariable Long id,
                                                @RequestBody MoveArchiveDocumentRequest request) {
        return Result.ok(archiveFolderService.moveDocument(id, request));
    }

    @Operation(summary = "移动正式档案目录", description = "将某个目录及其子目录下的正式档案移动到目标父目录，仅更新目录元数据")
    @PatchMapping("/api/archive-folders/move")
    public Result<MoveArchiveFolderResult> moveFolder(@RequestBody MoveArchiveFolderRequest request) {
        return Result.ok(archiveFolderService.moveFolder(request));
    }
}
