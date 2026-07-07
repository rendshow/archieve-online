package com.danganguan.archive.document.importing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.config.ArchiveStorageProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.importing.dto.FinishedArchiveImportResult;
import com.danganguan.archive.document.importing.entity.FinishedArchiveImportJob;
import com.danganguan.archive.document.importing.enums.FinishedArchiveImportJobStatus;
import com.danganguan.archive.document.importing.mapper.FinishedArchiveImportJobMapper;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
public class FinishedArchiveImportServiceImpl
        extends ServiceImpl<FinishedArchiveImportJobMapper, FinishedArchiveImportJob>
        implements FinishedArchiveImportService {
    private static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");
    private static final Set<String> SUPPORTED_ARCHIVE_EXTENSIONS = Set.of("zip", "7z");

    private final ArchiveHallService archiveHallService;
    private final ArchiveDocumentService archiveDocumentService;
    private final FileStorageService fileStorageService;
    private final ArchiveStorageProperties properties;
    private final TaskExecutor applicationTaskExecutor;

    @Override
    public FinishedArchiveImportResult importFinishedArchives(Long hallId, List<MultipartFile> files) {
        FinishedArchiveImportJob job = createImportJob(hallId, files);
        return new FinishedArchiveImportResult(
                hallId,
                value(job.getImportedCount()),
                value(job.getSkippedCount()),
                skippedPreview(job.getSkippedPreview()),
                List.of()
        );
    }

    @Override
    public FinishedArchiveImportJob createImportJob(Long hallId, List<MultipartFile> files) {
        ArchiveHall hall = validateRequest(hallId, files);
        String batchNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();

        FinishedArchiveImportJob job = new FinishedArchiveImportJob();
        job.setHallId(hall.getId());
        job.setBatchNo(batchNo);
        job.setStatus(FinishedArchiveImportJobStatus.STAGING);
        job.setTotalCount(0);
        job.setImportedCount(0);
        job.setSkippedCount(0);
        job.setSkippedPreview("");
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        save(job);

        Path sourceRoot = sourceRoot(job.getId());
        stageFiles(files, sourceRoot);
        job.setSourceRootPath(storageRoot().relativize(sourceRoot).toString().replace('\\', '/'));
        job.setStatus(FinishedArchiveImportJobStatus.PENDING);
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);

        applicationTaskExecutor.execute(() -> processJob(job.getId()));
        return job;
    }

    @Override
    public FinishedArchiveImportJob getImportJob(Long jobId) {
        FinishedArchiveImportJob job = getById(jobId);
        if (job == null) {
            throw new BizException("成品档案导入任务不存在");
        }
        return job;
    }

    @Override
    public void processJob(Long jobId) {
        FinishedArchiveImportJob job = getImportJob(jobId);
        if (job.getStatus() == FinishedArchiveImportJobStatus.IMPORTING
                || job.getStatus() == FinishedArchiveImportJobStatus.COMPLETED) {
            return;
        }
        ArchiveHall hall = archiveHallService.getById(job.getHallId());
        if (hall == null) {
            fail(job, "档案馆不存在");
            return;
        }
        Path sourceRoot = storageRoot().resolve(job.getSourceRootPath()).normalize();
        if (!sourceRoot.startsWith(storageRoot()) || !Files.exists(sourceRoot)) {
            fail(job, "导入暂存目录不存在");
            return;
        }

        ImportProgress progress = new ImportProgress(job, hall, sourceRoot);
        try {
            start(job);
            List<Path> stagedFiles;
            try (var walk = Files.walk(sourceRoot)) {
                stagedFiles = walk
                        .filter(Files::isRegularFile)
                        .sorted()
                        .toList();
            }
            int total = countImportableEntries(stagedFiles, sourceRoot);
            updateTotal(job, total);
            for (Path stagedFile : stagedFiles) {
                importStagedFile(stagedFile, progress);
            }
            complete(progress);
        } catch (Exception ex) {
            fail(job, ex.getMessage());
        }
    }

    private ArchiveHall validateRequest(Long hallId, List<MultipartFile> files) {
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
        return hall;
    }

    private void stageFiles(List<MultipartFile> files, Path sourceRoot) {
        try {
            Files.createDirectories(sourceRoot);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String relativePath = normalizeRelativePath(file.getOriginalFilename());
                if (relativePath.isBlank()) {
                    relativePath = UUID.randomUUID().toString();
                }
                Path target = sourceRoot.resolve(relativePath).normalize();
                if (!target.startsWith(sourceRoot)) {
                    throw new BizException("非法上传路径：" + file.getOriginalFilename());
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new BizException("暂存导入文件失败：" + ex.getMessage());
        }
    }

    private int countImportableEntries(List<Path> stagedFiles, Path sourceRoot) throws IOException {
        int total = 0;
        for (Path stagedFile : stagedFiles) {
            String ext = extension(stagedFile.getFileName().toString());
            if (SUPPORTED_ARCHIVE_EXTENSIONS.contains(ext)) {
                total += (int) archiveEntryNames(stagedFile, ext).stream()
                        .filter(name -> SUPPORTED_FILE_EXTENSIONS.contains(extension(name)))
                        .count();
            } else if (SUPPORTED_FILE_EXTENSIONS.contains(ext) && !isNestedArchivePath(sourceRoot, stagedFile)) {
                total++;
            }
        }
        return total;
    }

    private void importStagedFile(Path stagedFile, ImportProgress progress) throws IOException {
        String ext = extension(stagedFile.getFileName().toString());
        if (SUPPORTED_ARCHIVE_EXTENSIONS.contains(ext)) {
            importArchive(stagedFile, ext, progress);
            return;
        }
        String relativePath = progress.sourceRoot.relativize(stagedFile).toString().replace('\\', '/');
        if (!SUPPORTED_FILE_EXTENSIONS.contains(ext)) {
            progress.skip(relativePath + "：不支持的成品文件类型");
            return;
        }
        if (isNestedArchivePath(progress.sourceRoot, stagedFile)) {
            progress.skip(relativePath + "：压缩包内不支持二次压缩包");
            return;
        }
        try (InputStream input = Files.newInputStream(stagedFile)) {
            importOneFile(relativePath, input, progress);
        }
    }

    private boolean isNestedArchivePath(Path sourceRoot, Path path) {
        Path relative = sourceRoot.relativize(path);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String segment = relative.getName(i).toString();
            if (SUPPORTED_ARCHIVE_EXTENSIONS.contains(extension(segment))) {
                return true;
            }
        }
        return false;
    }

    private void importArchive(Path archivePath, String ext, ImportProgress progress) throws IOException {
        if ("zip".equals(ext)) {
            importZip(archivePath, progress);
        } else {
            importSevenZ(archivePath, progress);
        }
    }

    private void importZip(Path zipPath, ImportProgress progress) throws IOException {
        IOException last = null;
        for (Charset charset : List.of(StandardCharsets.UTF_8, Charset.forName("GBK"))) {
            try (ZipFile zipFile = new ZipFile(zipPath.toFile(), charset)) {
                List<? extends ZipEntry> entries = zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .sorted(Comparator.comparing(ZipEntry::getName))
                        .toList();
                for (ZipEntry entry : entries) {
                    importArchiveEntry(entry.getName(), zipFile.getInputStream(entry), progress);
                }
                return;
            } catch (IOException | IllegalArgumentException ex) {
                last = ex instanceof IOException ioException ? ioException : new IOException(ex.getMessage(), ex);
            }
        }
        throw last == null ? new IOException("无法读取 ZIP 文件") : last;
    }

    private void importSevenZ(Path archivePath, ImportProgress progress) throws IOException {
        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(archivePath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = readSevenZEntry(sevenZFile);
                importArchiveEntry(entry.getName(), new ByteArrayInputStream(bytes), progress);
            }
        }
    }

    private List<String> archiveEntryNames(Path archivePath, String ext) throws IOException {
        if ("zip".equals(ext)) {
            IOException last = null;
            for (Charset charset : List.of(StandardCharsets.UTF_8, Charset.forName("GBK"))) {
                try (ZipFile zipFile = new ZipFile(archivePath.toFile(), charset)) {
                    return zipFile.stream()
                            .filter(entry -> !entry.isDirectory())
                            .map(ZipEntry::getName)
                            .toList();
                } catch (IOException | IllegalArgumentException ex) {
                    last = ex instanceof IOException ioException ? ioException : new IOException(ex.getMessage(), ex);
                }
            }
            throw last == null ? new IOException("无法读取 ZIP 文件") : last;
        }
        List<String> names = new ArrayList<>();
        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(archivePath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
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

    private void importArchiveEntry(String entryName, InputStream input, ImportProgress progress) throws IOException {
        String relativePath = normalizeRelativePath(entryName);
        String ext = extension(relativePath);
        if (SUPPORTED_ARCHIVE_EXTENSIONS.contains(ext)) {
            progress.skip(relativePath + "：压缩包内不支持二次压缩包");
            input.close();
            return;
        }
        if (!SUPPORTED_FILE_EXTENSIONS.contains(ext)) {
            progress.skip(relativePath + "：不支持的成品文件类型");
            input.close();
            return;
        }
        try (input) {
            importOneFile(relativePath, input, progress);
        }
    }

    private void importOneFile(String relativePath, InputStream input, ImportProgress progress) {
        String normalizedPath = stripHallRoot(normalizeRelativePath(relativePath), progress.hall.getName());
        if (normalizedPath.isBlank() || !normalizedPath.contains(".")) {
            progress.skip(relativePath + "：文件路径无效");
            return;
        }

        String fileName = fileName(normalizedPath);
        String ext = extension(fileName);
        String folderPath = folderPath(normalizedPath);
        String title = stripExtension(fileName);
        String objectKey = "imported/" + progress.hall.getId() + "/" + progress.job.getBatchNo() + "/" + normalizedPath;
        StoredFile storedFile = fileStorageService.saveArchive(objectKey, input);

        LocalDateTime now = LocalDateTime.now();
        ArchiveDocument document = new ArchiveDocument();
        document.setHallId(progress.hall.getId());
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
        progress.imported();
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

    private void start(FinishedArchiveImportJob job) {
        job.setStatus(FinishedArchiveImportJobStatus.IMPORTING);
        job.setStartedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
    }

    private void updateTotal(FinishedArchiveImportJob job, int total) {
        job.setTotalCount(total);
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
    }

    private void complete(ImportProgress progress) {
        FinishedArchiveImportJob job = progress.job;
        job.setStatus(FinishedArchiveImportJobStatus.COMPLETED);
        job.setImportedCount(progress.importedCount);
        job.setSkippedCount(progress.skippedFiles.size());
        job.setSkippedPreview(joinSkipped(progress.skippedFiles));
        job.setFinishedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
    }

    private void fail(FinishedArchiveImportJob job, String message) {
        job.setStatus(FinishedArchiveImportJobStatus.FAILED);
        job.setErrorMessage(limit(message, 1000));
        job.setFinishedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
    }

    private void updateProgress(ImportProgress progress) {
        FinishedArchiveImportJob job = progress.job;
        job.setImportedCount(progress.importedCount);
        job.setSkippedCount(progress.skippedFiles.size());
        job.setSkippedPreview(joinSkipped(progress.skippedFiles));
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
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

    private Path storageRoot() {
        return Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    private Path sourceRoot(Long jobId) {
        return storageRoot().resolve("import-jobs").resolve(String.valueOf(jobId)).resolve("source").normalize();
    }

    private List<String> skippedPreview(String skippedPreview) {
        if (skippedPreview == null || skippedPreview.isBlank()) {
            return List.of();
        }
        return List.of(skippedPreview.split("\\n"));
    }

    private String joinSkipped(List<String> skippedFiles) {
        return limit(String.join("\n", skippedFiles.stream().limit(50).toList()), 4000);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private class ImportProgress {
        private final FinishedArchiveImportJob job;
        private final ArchiveHall hall;
        private final Path sourceRoot;
        private final List<String> skippedFiles = new ArrayList<>();
        private int importedCount;
        private int changedSinceUpdate;

        private ImportProgress(FinishedArchiveImportJob job, ArchiveHall hall, Path sourceRoot) {
            this.job = job;
            this.hall = hall;
            this.sourceRoot = sourceRoot;
            this.importedCount = value(job.getImportedCount());
        }

        private void imported() {
            importedCount++;
            changed();
        }

        private void skip(String reason) {
            skippedFiles.add(reason);
            changed();
        }

        private void changed() {
            changedSinceUpdate++;
            if (changedSinceUpdate >= 20) {
                changedSinceUpdate = 0;
                updateProgress(this);
            }
        }
    }
}
