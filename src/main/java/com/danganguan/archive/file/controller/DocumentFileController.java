package com.danganguan.archive.file.controller;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import com.danganguan.archive.workspace.service.WorkspaceDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Tag(name = "档案文件", description = "处理后文件预览和下载接口")
@RestController
@RequiredArgsConstructor
public class DocumentFileController {
    private final WorkspaceDocumentService workspaceDocumentService;
    private final ArchiveDocumentService archiveDocumentService;
    private final FileStorageService fileStorageService;

    @Operation(summary = "预览工作区处理后文件", description = "根据工作区档案 ID 预览或下载处理后的 PDF/PNG 文件")
    @GetMapping("/api/workspace-documents/{id}/file")
    public ResponseEntity<Resource> workspaceFile(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "false") boolean download) {
        WorkspaceDocument document = workspaceDocumentService.getById(id);
        if (document == null) {
            throw new BizException("工作区文件不存在");
        }
        return fileResponse(
                document.getStoragePath(),
                document.getFinalName(),
                document.getOutputFormat(),
                download
        );
    }

    @Operation(summary = "预览正式档案文件", description = "根据正式档案 ID 预览或下载入库后的 PDF/PNG 文件")
    @GetMapping("/api/archive-documents/{id}/file")
    public ResponseEntity<Resource> archiveFile(@PathVariable Long id,
                                                @RequestParam(defaultValue = "false") boolean download) {
        ArchiveDocument document = archiveDocumentService.getById(id);
        if (document == null) {
            throw new BizException("正式档案不存在");
        }
        return fileResponse(
                document.getStoragePath(),
                document.getTitle(),
                document.getFileFormat(),
                download
        );
    }

    private ResponseEntity<Resource> fileResponse(String storagePath, String name, OutputFormat format, boolean download) {
        Path path = fileStorageService.resolve(storagePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BizException("文件不存在或已被移动");
        }
        String filename = filename(name, path, format);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        try {
            return ResponseEntity.ok()
                    .contentType(mediaType(path, format))
                    .contentLength(Files.size(path))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new PathResource(path));
        } catch (IOException ex) {
            throw new BizException("读取文件失败：" + ex.getMessage());
        }
    }

    private MediaType mediaType(Path path, OutputFormat format) {
        String ext = ext(path.getFileName().toString());
        if (format == OutputFormat.PDF || "pdf".equals(ext)) {
            return MediaType.APPLICATION_PDF;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "tif", "tiff" -> MediaType.parseMediaType("image/tiff");
            default -> format == OutputFormat.PNG ? MediaType.IMAGE_PNG : MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private String filename(String name, Path path, OutputFormat format) {
        String safeName = safeName(name == null || name.isBlank() ? stripExt(path.getFileName().toString()) : name);
        String ext = "." + outputExt(path, format);
        if (safeName.toLowerCase().endsWith(ext)) {
            return safeName;
        }
        return safeName + ext;
    }

    private String outputExt(Path path, OutputFormat format) {
        String ext = ext(path.getFileName().toString());
        if (!ext.isBlank()) {
            return ext;
        }
        return format == OutputFormat.PNG ? "png" : "pdf";
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private String ext(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String safeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
