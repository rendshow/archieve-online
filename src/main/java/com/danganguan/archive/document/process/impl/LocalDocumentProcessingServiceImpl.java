package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.process.DocumentProcessingService;
import com.danganguan.archive.document.process.ImageEnhanceService;
import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class LocalDocumentProcessingServiceImpl implements DocumentProcessingService {
    private static final float PDF_MARGIN = 24F;
    private static final long HEIC_CONVERT_TIMEOUT_SECONDS = 60L;
    private static final long ENHANCE_INPUT_MAX_BYTES = 4_800_000L;
    private static final int ENHANCE_INPUT_MAX_EDGE = 2200;

    private final FileStorageService fileStorageService;
    private final ImageEnhanceService imageEnhanceService;

    @Override
    public List<ProcessedFileResult> process(ArchiveTask task, UploadedFile file) {
        if (file.getUploadType() != UploadType.ZIP) {
            throw new BizException("当前处理链路仅支持图片压缩包（.zip）");
        }
        return processImageZip(task, file);
    }

    @Override
    public List<ProcessedFileResult> processGroup(ArchiveTask task, List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BizException("输入组中没有可处理文件");
        }
        List<UploadedFile> orderedFiles = files.stream()
                .sorted(Comparator.comparing(UploadedFile::getGroupOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<ProcessedFileResult> results = new ArrayList<>();
        for (UploadedFile file : orderedFiles) {
            results.addAll(process(task, file));
        }
        return results;
    }

    private List<ProcessedFileResult> processImageZip(ArchiveTask task, UploadedFile file) {
        Path source = fileStorageService.resolve(file.getStoragePath());
        ZipImageArchive archive = extractImageZip(task.getId(), source);
        try {
            List<ImageInput> images = enhanceImagesIfNeeded(task, archive.images());
            if (task.getOutputFormat() == OutputFormat.PNG) {
                return imagesToImageFiles(task, images, stripExt(file.getOriginalName()));
            }
            return imagesToPdfByStrategy(task, images, stripExt(file.getOriginalName()));
        } finally {
            deleteTempDir(archive.tempDir());
        }
    }

    private ZipImageArchive extractImageZip(Long taskId, Path source) {
        Path tempDir = fileStorageService.prepareWorkspaceFile(taskId, "zip-" + UUID.randomUUID()).normalize();
        try {
            Files.createDirectories(tempDir);
            List<ImageInput> images = extractArchiveImages(source, tempDir);
            images.sort((left, right) -> naturalCompare(left.entryName(), right.entryName()));
            if (images.isEmpty()) {
                throw new BizException("压缩包中没有可处理的图片");
            }
            return new ZipImageArchive(tempDir, images);
        } catch (IOException ex) {
            deleteTempDir(tempDir);
            throw new BizException("解压图片压缩包失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            deleteTempDir(tempDir);
            throw ex;
        }
    }

    private List<ImageInput> extractArchiveImages(Path source, Path tempDir) throws IOException {
        List<ImageInput> images = new ArrayList<>();
        String archiveExt = ext(source.getFileName().toString());
        if ("7z".equals(archiveExt)) {
            extract7zImages(source, tempDir, images);
            return images;
        }
        try {
            unzipImagesWithCharset(source, tempDir, images, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            clearDirectory(tempDir);
            unzipImagesWithCharset(source, tempDir, images, Charset.forName("GBK"));
        }
        return images;
    }

    private void unzipImagesWithCharset(Path source, Path tempDir, List<ImageInput> images, Charset charset) throws IOException {
        try (InputStream input = Files.newInputStream(source);
             ZipInputStream zipInput = new ZipInputStream(input, charset)) {
            ZipEntry entry;
            int order = images.size();
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace('\\', '/');
                String ext = ext(entryName);
                if ("pdf".equals(ext)) {
                    throw new BizException("当前仅支持图片压缩包，压缩包内发现 PDF：" + filename(entryName));
                }
                if (!isImageExt(ext)) {
                    continue;
                }
                String originalFilename = filename(entryName);
                Path target = tempDir.resolve(UUID.randomUUID() + "-" + originalFilename).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new BizException("压缩包内包含非法路径");
                }
                Files.copy(zipInput, target, StandardCopyOption.REPLACE_EXISTING);
                images.add(new ImageInput(target, entryName, stripExt(originalFilename), normalizeImageExt(ext), ++order));
            }
        }
    }

    private void extract7zImages(Path source, Path tempDir, List<ImageInput> images) throws IOException {
        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(source).get()) {
            SevenZArchiveEntry entry;
            int order = images.size();
            byte[] buffer = new byte[8192];
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace('\\', '/');
                String ext = ext(entryName);
                if ("pdf".equals(ext)) {
                    throw new BizException("当前仅支持图片压缩包，压缩包内发现 PDF：" + filename(entryName));
                }
                if (!isImageExt(ext)) {
                    continue;
                }
                String originalFilename = filename(entryName);
                Path target = tempDir.resolve(UUID.randomUUID() + "-" + originalFilename).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new BizException("压缩包内包含非法路径");
                }
                try (OutputStream output = Files.newOutputStream(target)) {
                    int length;
                    while ((length = sevenZFile.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                }
                images.add(new ImageInput(target, entryName, stripExt(originalFilename), normalizeImageExt(ext), ++order));
            }
        }
    }

    private List<ImageInput> enhanceImagesIfNeeded(ArchiveTask task, List<ImageInput> images) {
        if (!Boolean.TRUE.equals(task.getEnableScanEnhance())) {
            return images;
        }
        List<ImageInput> enhancedImages = new ArrayList<>();
        for (ImageInput image : images) {
            Path enhanceInput = prepareEnhanceInput(image);
            Path enhancedPath = imageEnhanceService.enhance(task, enhanceInput);
            if (enhancedPath == null) {
                throw new BizException("扫描图像增强未返回有效图片");
            }
            String enhancedExt = ext(enhancedPath.getFileName().toString());
            enhancedImages.add(new ImageInput(
                    enhancedPath,
                    image.entryName(),
                    image.baseName(),
                    isImageExt(enhancedExt) ? normalizeImageExt(enhancedExt) : image.ext(),
                    image.order()
            ));
        }
        return enhancedImages;
    }

    private Path prepareEnhanceInput(ImageInput image) {
        Path readableImage = normalizeReadableImage(image);
        try {
            BufferedImage bufferedImage = readImage(readableImage);
            int maxEdge = Math.max(bufferedImage.getWidth(), bufferedImage.getHeight());
            if (Files.size(readableImage) <= ENHANCE_INPUT_MAX_BYTES && maxEdge <= ENHANCE_INPUT_MAX_EDGE
                    && ("jpg".equals(ext(readableImage.getFileName().toString()))
                    || "jpeg".equals(ext(readableImage.getFileName().toString())))) {
                return readableImage;
            }
            Path target = readableImage.resolveSibling(stripExt(readableImage.getFileName().toString()) + ".enhance-input.jpg");
            writeCompressedJpeg(bufferedImage, target);
            return target;
        } catch (IOException ex) {
            throw new BizException("准备图像增强输入失败：" + ex.getMessage());
        }
    }

    private List<ProcessedFileResult> imagesToImageFiles(ArchiveTask task, List<ImageInput> images, String baseName) {
        if (!splitStrategy(task).isSinglePerson()) {
            throw new BizException("输出为图片时仅支持单人单组策略，固定元素拆分和 AI 边界拆分只支持 PDF 输出");
        }
        List<ProcessedFileResult> results = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ImageInput image = images.get(i);
            String suffix = images.size() == 1 ? "" : "-第" + (i + 1) + "张";
            boolean heic = isHeicExt(image.ext());
            Path readableImage = normalizeReadableImage(image);
            Path target = workspaceFile(task.getId(), firstNonBlank(image.baseName(), baseName) + suffix, heic ? "jpg" : image.ext());
            copy(readableImage, target);
            results.add(new ProcessedFileResult(fileStorageService.toRelativePath(target), OutputFormat.PNG, 1, ""));
        }
        return results;
    }

    private List<ProcessedFileResult> imagesToPdfByStrategy(ArchiveTask task, List<ImageInput> images, String baseName) {
        List<List<ImageInput>> groups = groupImages(task, images);
        List<ProcessedFileResult> results = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<ImageInput> group = groups.get(i);
            String suffix = groups.size() == 1 ? "" : "-第" + (i + 1) + "组";
            Path target = workspaceFile(task.getId(), baseName + suffix, "pdf");
            writeImagesToPdf(group.stream().map(ImageInput::path).toList(), target);
            results.add(new ProcessedFileResult(fileStorageService.toRelativePath(target), OutputFormat.PDF, group.size(), ""));
        }
        return results;
    }

    private List<List<ImageInput>> groupImages(ArchiveTask task, List<ImageInput> images) {
        PersonSplitStrategy strategy = splitStrategy(task);
        if (strategy.isFixedElementsPerPerson()) {
            int fixedCount = task.getFixedElementsPerPerson() == null ? 0 : task.getFixedElementsPerPerson();
            if (fixedCount <= 0) {
                throw new BizException("固定元素数归档时，每人对应元素数必须大于0");
            }
            if (images.size() % fixedCount != 0) {
                throw new BizException("固定元素数拆分要求图片总数必须是 n 的倍数：当前图片数 "
                        + images.size() + "，每人元素数 " + fixedCount);
            }
            List<List<ImageInput>> groups = new ArrayList<>();
            for (int i = 0; i < images.size(); i += fixedCount) {
                groups.add(images.subList(i, i + fixedCount));
            }
            return groups;
        }
        if (strategy.isAiPersonBoundary()) {
            return splitImagesByAiBoundary(images);
        }
        return List.of(images);
    }

    private List<List<ImageInput>> splitImagesByAiBoundary(List<ImageInput> images) {
        // AI 边界识别服务接入前，保持每个压缩包至少产出一个完整 PDF，且不跨压缩包合并。
        return List.of(images);
    }

    private void writeImagesToPdf(List<Path> images, Path target) {
        try (PDDocument document = new PDDocument()) {
            for (Path imagePath : images) {
                BufferedImage image = readImage(normalizeReadableImage(imagePath));
                float width = image.getWidth();
                float height = image.getHeight();
                PDPage page = new PDPage(new PDRectangle(width + PDF_MARGIN * 2, height + PDF_MARGIN * 2));
                document.addPage(page);
                PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdfImage, PDF_MARGIN, PDF_MARGIN, width, height);
                }
            }
            document.save(target.toFile());
        } catch (IOException ex) {
            throw new BizException("图片转 PDF 失败：" + ex.getMessage());
        }
    }

    private BufferedImage readImage(Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                throw new BizException("无法读取图片：" + imagePath.getFileName());
            }
            return image;
        } catch (IOException ex) {
            throw new BizException("读取图片失败：" + ex.getMessage());
        }
    }

    private Path normalizeReadableImage(ImageInput image) {
        return isHeicExt(image.ext()) ? convertHeicToPng(image.path()) : image.path();
    }

    private Path normalizeReadableImage(Path imagePath) {
        return isHeicExt(ext(imagePath.getFileName().toString())) ? convertHeicToPng(imagePath) : imagePath;
    }

    private Path convertHeicToPng(Path source) {
        Path target = source.resolveSibling(stripExt(source.getFileName().toString()) + ".jpg");
        if (Files.exists(target)) {
            return target;
        }
        List<String> command = List.of(
                "python",
                Path.of("scripts/image/convert_heic.py").toAbsolutePath().toString(),
                source.toAbsolutePath().toString(),
                target.toAbsolutePath().toString()
        );
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process = processBuilder.start();
            boolean finished = process.waitFor(HEIC_CONVERT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException("HEIC 转 PNG 超时：" + source.getFileName());
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new BizException("HEIC 转 PNG 失败：" + firstNonBlank(error, output));
            }
            return target;
        } catch (IOException ex) {
            throw new BizException("HEIC 转 PNG 失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException("HEIC 转 PNG 被中断");
        }
    }

    private void writeCompressedJpeg(BufferedImage source, Path target) {
        BufferedImage resized = resizeIfNeeded(source);
        float quality = 0.85F;
        while (true) {
            writeJpegWithQuality(resized, target, quality);
            try {
                if (Files.size(target) <= ENHANCE_INPUT_MAX_BYTES || quality <= 0.60F) {
                    break;
                }
            } catch (IOException ex) {
                throw new BizException("压缩图像增强输入失败：" + ex.getMessage());
            }
            quality -= 0.05F;
        }
    }

    private void writeJpegWithQuality(BufferedImage image, Path target, float quality) {
        try (var output = ImageIO.createImageOutputStream(target.toFile())) {
            var writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new BizException("当前环境不支持 JPEG 压缩");
            }
            var writer = writers.next();
            try {
                writer.setOutput(output);
                var params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality);
                    writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
                } else {
                    writer.write(image);
                }
            } finally {
                writer.dispose();
            }
        } catch (IOException ex) {
            throw new BizException("压缩图像增强输入失败：" + ex.getMessage());
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int maxEdge = Math.max(width, height);
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D rgbGraphics = rgb.createGraphics();
        try {
            rgbGraphics.drawImage(source, 0, 0, null);
        } finally {
            rgbGraphics.dispose();
        }
        if (maxEdge <= ENHANCE_INPUT_MAX_EDGE) {
            return rgb;
        }
        double scale = (double) ENHANCE_INPUT_MAX_EDGE / maxEdge;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(rgb, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private void copy(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BizException("复制图片失败：" + ex.getMessage());
        }
    }

    private Path workspaceFile(Long taskId, String baseName, String ext) {
        return fileStorageService.prepareWorkspaceFile(taskId, safeName(baseName) + "-" + UUID.randomUUID() + "." + ext);
    }

    private PersonSplitStrategy splitStrategy(ArchiveTask task) {
        return task.getPersonSplitStrategy() == null ? PersonSplitStrategy.SINGLE_PERSON : task.getPersonSplitStrategy();
    }

    private void clearDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(dir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private void deleteTempDir(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private int naturalCompare(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = numberEnd(left, leftIndex);
                int rightEnd = numberEnd(right, rightIndex);
                long leftNumber = Long.parseLong(left.substring(leftIndex, leftEnd));
                long rightNumber = Long.parseLong(right.substring(rightIndex, rightEnd));
                int numberCompare = Long.compare(leftNumber, rightNumber);
                if (numberCompare != 0) {
                    return numberCompare;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            int charCompare = Character.compare(Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (charCompare != 0) {
                return charCompare;
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length(), right.length());
    }

    private int numberEnd(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private boolean isImageExt(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff", "heic", "heif" -> true;
            default -> false;
        };
    }

    private boolean isHeicExt(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "heic", "heif" -> true;
            default -> false;
        };
    }

    private String normalizeImageExt(String ext) {
        return ext.toLowerCase(Locale.ROOT);
    }

    private String ext(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExt(String filename) {
        if (filename == null) {
            return "document";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private String filename(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String safeName(String name) {
        String safe = name == null || name.isBlank() ? "document" : name;
        return safe.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private record ZipImageArchive(Path tempDir, List<ImageInput> images) {
    }

    private record ImageInput(Path path, String entryName, String baseName, String ext, int order) {
    }
}
