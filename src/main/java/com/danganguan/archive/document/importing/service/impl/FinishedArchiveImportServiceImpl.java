package com.danganguan.archive.document.importing.service.impl;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.importing.dto.FinishedArchiveImportResult;
import com.danganguan.archive.document.importing.service.FinishedArchiveImportService;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
import com.danganguan.archive.hall.entity.ArchiveHall;
import com.danganguan.archive.hall.service.ArchiveHallService;
import com.danganguan.archive.task.enums.OutputFormat;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class FinishedArchiveImportServiceImpl implements FinishedArchiveImportService {
    private static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");
    private static final Set<String> SUPPORTED_ARCHIVE_EXTENSIONS = Set.of("zip", "7z");

    private final ArchiveHallService archiveHallService;
    private final ArchiveDocumentService archiveDocumentService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public FinishedArchiveImportResult importFinishedArchives(Long hallId, List<MultipartFile> files) {
        if (hallId == null) {
            throw new BizException("馆 ID 不能为空");
        }
        ArchiveHall hall = archiveHallService.getById(hallId);
        if (hall == null) {
            throw new BizException("档案馆不存在");
        }
        if (files == null || files.isEmpty()) {
            throw new BizException("请上传文件夹文件或压缩包");
        }

        String batchNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        ImportCollector collector = new ImportCollector(hall, batchNo);
        for (MultipartFile file : files) {
            importMultipart(file, collector);
        }
        return new FinishedArchiveImportResult(
                hallId,
                collector.documents.size(),
                collector.skippedFiles.size(),
                collector.skippedFiles,
                collector.documents
        );
    }

    private void importMultipart(MultipartFile file, ImportCollector collector) {
        if (file == null || file.isEmpty()) {
            collector.skip("空文件");
            return;
        }
        String originalPath = normalizeRelativePath(file.getOriginalFilename());
        String ext = extension(originalPath);
        if (SUPPORTED_ARCHIVE_EXTENSIONS.contains(ext)) {
            importArchive(file, ext, collector);
            return;
        }
        if (!SUPPORTED_FILE_EXTENSIONS.contains(ext)) {
            collector.skip(originalPath + "：不支持的成品文件类型");
            return;
        }
        try (InputStream input = file.getInputStream()) {
            importOneFile(originalPath, input, collector);
        } catch (IOException ex) {
            throw new BizException("读取上传文件失败：" + ex.getMessage());
        }
    }

    private void importArchive(MultipartFile file, String ext, ImportCollector collector) {
        Path temp = null;
        try {
            temp = Files.createTempFile("finished-archive-import-", "." + ext);
            file.transferTo(temp);
            if ("zip".equals(ext)) {
                importZip(temp, collector);
            } else {
                importSevenZ(temp, collector);
            }
        } catch (IOException ex) {
            throw new BizException("解析压缩包失败：" + ex.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Temporary file cleanup failure does not affect import result.
                }
            }
        }
    }

    private void importZip(Path zipPath, ImportCollector collector) throws IOException {
        IOException last = null;
        for (Charset charset : List.of(StandardCharsets.UTF_8, Charset.forName("GBK"))) {
            try (ZipFile zipFile = new ZipFile(zipPath.toFile(), charset)) {
                List<? extends ZipEntry> entries = zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .sorted(Comparator.comparing(ZipEntry::getName))
                        .toList();
                for (ZipEntry entry : entries) {
                    importArchiveEntry(entry.getName(), zipFile.getInputStream(entry), collector);
                }
                return;
            } catch (IOException | IllegalArgumentException ex) {
                last = ex instanceof IOException ioException ? ioException : new IOException(ex.getMessage(), ex);
            }
        }
        throw last == null ? new IOException("无法读取 ZIP 文件") : last;
    }

    private void importSevenZ(Path archivePath, ImportCollector collector) throws IOException {
        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(archivePath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = readSevenZEntry(sevenZFile);
                importArchiveEntry(entry.getName(), new ByteArrayInputStream(bytes), collector);
            }
        }
    }

    private byte[] readSevenZEntry(SevenZFile sevenZFile) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = sevenZFile.read(buffer)) > 0) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private void importArchiveEntry(String entryName, InputStream input, ImportCollector collector) throws IOException {
        String relativePath = normalizeRelativePath(entryName);
        String ext = extension(relativePath);
        if (SUPPORTED_ARCHIVE_EXTENSIONS.contains(ext)) {
            collector.skip(relativePath + "：压缩包内不支持二次压缩包");
            input.close();
            return;
        }
        if (!SUPPORTED_FILE_EXTENSIONS.contains(ext)) {
            collector.skip(relativePath + "：不支持的成品文件类型");
            input.close();
            return;
        }
        try (input) {
            importOneFile(relativePath, input, collector);
        }
    }

    private void importOneFile(String relativePath, InputStream input, ImportCollector collector) {
        String normalizedPath = stripHallRoot(normalizeRelativePath(relativePath), collector.hall.getName());
        if (normalizedPath.isBlank() || !normalizedPath.contains(".")) {
            collector.skip(relativePath + "：文件路径无效");
            return;
        }

        String fileName = fileName(normalizedPath);
        String ext = extension(fileName);
        String folderPath = folderPath(normalizedPath);
        String title = stripExtension(fileName);
        String objectKey = "imported/" + collector.hall.getId() + "/" + collector.batchNo + "/" + normalizedPath;
        StoredFile storedFile = fileStorageService.saveArchive(objectKey, input);

        LocalDateTime now = LocalDateTime.now();
        ArchiveDocument document = new ArchiveDocument();
        document.setHallId(collector.hall.getId());
        document.setTaskId(null);
        document.setWorkspaceDocumentId(null);
        document.setArchiveNo(buildImportArchiveNo(now));
        document.setTitle(title);
        document.setFolderName(firstPathSegment(folderPath));
        document.setFolderPath(folderPath);
        document.setFileFormat(outputFormat(ext));
        document.setStoragePath(storedFile.relativePath());
        document.setPageCount(pageCount(storedFile.relativePath(), ext));
        document.setAiSummary("成品档案导入，保留原始文件名和目录层级。");
        document.setOcrText(null);
        document.setStatus(ArchiveDocumentStatus.ACTIVE);
        document.setArchivedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setDeleted(0);
        archiveDocumentService.save(document);
        collector.documents.add(document);
    }

    private int pageCount(String storagePath, String ext) {
        if (!"pdf".equals(ext)) {
            return 1;
        }
        try (PDDocument document = PDDocument.load(fileStorageService.resolve(storagePath).toFile())) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            return 1;
        }
    }

    private OutputFormat outputFormat(String ext) {
        return switch (ext) {
            case "pdf" -> OutputFormat.PDF;
            case "png" -> OutputFormat.PNG;
            case "jpeg" -> OutputFormat.JPEG;
            default -> OutputFormat.JPG;
        };
    }

    private String buildImportArchiveNo(LocalDateTime now) {
        return "IMP" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String stripHallRoot(String relativePath, String hallName) {
        String normalizedHallName = hallName == null ? "" : hallName.trim();
        if (normalizedHallName.isBlank()) {
            return relativePath;
        }
        String prefix = normalizedHallName + "/";
        if (relativePath.startsWith(prefix)) {
            return relativePath.substring(prefix.length());
        }
        return relativePath;
    }

    private String normalizeRelativePath(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/').trim();
        normalized = normalized.replaceAll("^[A-Za-z]:/+", "");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            return "";
        }
        return normalized;
    }

    private String folderPath(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? "" : relativePath.substring(0, slash);
    }

    private String firstPathSegment(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return "";
        }
        int slash = folderPath.indexOf('/');
        return slash < 0 ? folderPath : folderPath.substring(0, slash);
    }

    private String fileName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static class ImportCollector {
        private final ArchiveHall hall;
        private final String batchNo;
        private final List<String> skippedFiles = new ArrayList<>();
        private final List<ArchiveDocument> documents = new ArrayList<>();

        private ImportCollector(ArchiveHall hall, String batchNo) {
            this.hall = hall;
            this.batchNo = batchNo;
        }

        private void skip(String reason) {
            skippedFiles.add(reason);
        }
    }
}
