package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.process.DocumentProcessingService;
import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class LocalDocumentProcessingServiceImpl implements DocumentProcessingService {
    private static final float PDF_MARGIN = 24F;

    private final FileStorageService fileStorageService;

    public LocalDocumentProcessingServiceImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public List<ProcessedFileResult> process(ArchiveTask task, UploadedFile file) {
        Path source = fileStorageService.resolve(file.getStoragePath());
        return switch (file.getUploadType()) {
            case PDF -> processPdf(task, source, stripExt(file.getOriginalName()));
            case IMAGE -> processImages(task, List.of(source), stripExt(file.getOriginalName()));
            case ZIP -> processZip(task, source);
            case UNKNOWN -> throw new BizException("暂不支持的文件类型：" + file.getOriginalName());
        };
    }

    @Override
    public List<ProcessedFileResult> processGroup(ArchiveTask task, List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BizException("输入组中没有可处理文件");
        }
        List<UploadedFile> orderedFiles = files.stream()
                .sorted(Comparator.comparing(UploadedFile::getGroupOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (orderedFiles.size() == 1 && orderedFiles.get(0).getUploadType() != UploadType.IMAGE) {
            return process(task, orderedFiles.get(0));
        }

        boolean allImages = orderedFiles.stream().allMatch(file -> file.getUploadType() == UploadType.IMAGE);
        if (!allImages) {
            List<ProcessedFileResult> results = new ArrayList<>();
            for (UploadedFile file : orderedFiles) {
                results.addAll(process(task, file));
            }
            return results;
        }

        List<Path> images = orderedFiles.stream()
                .map(file -> fileStorageService.resolve(file.getStoragePath()))
                .toList();
        String baseName = stripExt(orderedFiles.get(0).getOriginalName());
        return processImages(task, images, baseName);
    }

    private List<ProcessedFileResult> processPdf(ArchiveTask task, Path source, String baseName) {
        if (task.getOutputFormat() == OutputFormat.PDF) {
            Path target = workspaceFile(task.getId(), baseName, "pdf");
            copy(source, target);
            return List.of(new ProcessedFileResult(fileStorageService.toRelativePath(target), OutputFormat.PDF, countPdfPages(target), ""));
        }
        return renderPdfToPng(task.getId(), source, baseName);
    }

    private List<ProcessedFileResult> processImages(ArchiveTask task, List<Path> images, String baseName) {
        if (images.isEmpty()) {
            throw new BizException("没有可处理的图片");
        }
        if (task.getOutputFormat() == OutputFormat.PDF) {
            return imagesToPdfByStrategy(task, images, baseName);
        }
        return imagesToPngByStrategy(task, images, baseName);
    }

    private List<ProcessedFileResult> processZip(ArchiveTask task, Path source) {
        List<Path> pdfFiles = new ArrayList<>();
        List<Path> imageFiles = new ArrayList<>();
        Path tempDir = fileStorageService.prepareWorkspaceFile(task.getId(), "zip-" + UUID.randomUUID()).normalize();
        try {
            Files.createDirectories(tempDir);
            try {
                unzipWithCharset(source, tempDir, pdfFiles, imageFiles, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // Windows ZIP (GBK) fallback
                pdfFiles.clear();
                imageFiles.clear();
                unzipWithCharset(source, tempDir, pdfFiles, imageFiles, java.nio.charset.Charset.forName("GBK"));
            }
        } catch (IOException ex) {
            throw new BizException("解压文件失败：" + ex.getMessage());
        }

        pdfFiles.sort(Comparator.comparing(Path::toString));
        imageFiles.sort(Comparator.comparing(Path::toString));

        List<ProcessedFileResult> results = new ArrayList<>();
        int pdfIndex = 1;
        for (Path pdf : pdfFiles) {
            for (ProcessedFileResult result : processPdf(task, pdf, stripExt(pdf.getFileName().toString()))) {
                results.add(withSuffix(result, "-PDF" + pdfIndex));
            }
            pdfIndex++;
        }
        if (!imageFiles.isEmpty()) {
            results.addAll(processImages(task, imageFiles, stripExt(source.getFileName().toString())));
        }
        if (results.isEmpty()) {
            throw new BizException("压缩包中没有可处理的 PDF 或图片");
        }
        return results;
    }

    private void unzipWithCharset(Path source, Path tempDir, List<Path> pdfFiles, List<Path> imageFiles, java.nio.charset.Charset charset) throws IOException {
        try (InputStream input = Files.newInputStream(source);
             java.util.zip.ZipInputStream zipInput = new java.util.zip.ZipInputStream(input, charset)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String ext = ext(entry.getName());
                if (!isImageExt(ext) && !"pdf".equals(ext)) {
                    continue;
                }
                Path target = tempDir.resolve(UUID.randomUUID() + "-" + filename(entry.getName())).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new BizException("压缩包内包含非法路径");
                }
                Files.copy(zipInput, target, StandardCopyOption.REPLACE_EXISTING);
                if ("pdf".equals(ext)) {
                    pdfFiles.add(target);
                } else {
                    imageFiles.add(target);
                }
            }
        }
    }

    private List<ProcessedFileResult> imagesToPdfByStrategy(ArchiveTask task, List<Path> images, String baseName) {
        List<List<Path>> groups = groupImages(task, images);
        List<ProcessedFileResult> results = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<Path> group = groups.get(i);
            String suffix = groups.size() == 1 ? "" : "-第" + (i + 1) + "组";
            Path target = workspaceFile(task.getId(), baseName + suffix, "pdf");
            writeImagesToPdf(group, target);
            results.add(new ProcessedFileResult(fileStorageService.toRelativePath(target), OutputFormat.PDF, group.size(), suffix));
        }
        return results;
    }

    private List<ProcessedFileResult> imagesToPngByStrategy(ArchiveTask task, List<Path> images, String baseName) {
        List<List<Path>> groups = groupImages(task, images);
        List<ProcessedFileResult> results = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<Path> group = groups.get(i);
            String suffix = groups.size() == 1 ? "" : "-第" + (i + 1) + "组";
            Path target = workspaceFile(task.getId(), baseName + suffix, "png");
            writeImagesToPng(group, target);
            results.add(new ProcessedFileResult(fileStorageService.toRelativePath(target), OutputFormat.PNG, group.size(), suffix));
        }
        return results;
    }

    private List<List<Path>> groupImages(ArchiveTask task, List<Path> images) {
        PersonSplitStrategy strategy = task.getPersonSplitStrategy() == null
                ? PersonSplitStrategy.SINGLE_PERSON
                : task.getPersonSplitStrategy();
        if (strategy.isFixedElementsPerPerson()) {
            int fixedCount = task.getFixedElementsPerPerson() == null ? 0 : task.getFixedElementsPerPerson();
            if (fixedCount <= 0) {
                throw new BizException("固定元素数归档时，每人对应元素数必须大于0");
            }
            List<List<Path>> groups = new ArrayList<>();
            for (int i = 0; i < images.size(); i += fixedCount) {
                groups.add(images.subList(i, Math.min(i + fixedCount, images.size())));
            }
            return groups;
        }
        return List.of(images);
    }

    private List<ProcessedFileResult> renderPdfToPng(Long taskId, Path source, String baseName) {
        try (PDDocument document = PDDocument.load(source.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<ProcessedFileResult> results = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                String suffix = "-第" + (pageIndex + 1) + "页";
                Path target = workspaceFile(taskId, baseName + suffix, "png");
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 180, ImageType.RGB);
                ImageIO.write(image, "png", target.toFile());
                results.add(new ProcessedFileResult(fileStorageService.toRelativePath(target), OutputFormat.PNG, 1, suffix));
            }
            return results;
        } catch (IOException ex) {
            throw new BizException("PDF 转 PNG 失败：" + ex.getMessage());
        }
    }

    private void writeImagesToPdf(List<Path> images, Path target) {
        try (PDDocument document = new PDDocument()) {
            for (Path imagePath : images) {
                BufferedImage image = readImage(imagePath);
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

    private void writeImageAsPng(Path imagePath, Path target) {
        try {
            ImageIO.write(readImage(imagePath), "png", target.toFile());
        } catch (IOException ex) {
            throw new BizException("图片转 PNG 失败：" + ex.getMessage());
        }
    }

    private void writeImagesToPng(List<Path> images, Path target) {
        if (images.size() == 1) {
            writeImageAsPng(images.get(0), target);
            return;
        }
        try {
            List<BufferedImage> bufferedImages = images.stream().map(this::readImage).toList();
            int width = bufferedImages.stream().mapToInt(BufferedImage::getWidth).max().orElseThrow();
            int height = bufferedImages.stream().mapToInt(BufferedImage::getHeight).sum();
            BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = combined.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                int y = 0;
                for (BufferedImage image : bufferedImages) {
                    graphics.drawImage(image, 0, y, null);
                    y += image.getHeight();
                }
            } finally {
                graphics.dispose();
            }
            ImageIO.write(combined, "png", target.toFile());
        } catch (IOException ex) {
            throw new BizException("图片合并为 PNG 失败：" + ex.getMessage());
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

    private int countPdfPages(Path pdfPath) {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            throw new BizException("读取 PDF 页数失败：" + ex.getMessage());
        }
    }

    private void copy(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BizException("复制文件失败：" + ex.getMessage());
        }
    }

    private Path workspaceFile(Long taskId, String baseName, String ext) {
        return fileStorageService.prepareWorkspaceFile(taskId, safeName(baseName) + "-" + UUID.randomUUID() + "." + ext);
    }

    private ProcessedFileResult withSuffix(ProcessedFileResult result, String suffix) {
        return new ProcessedFileResult(result.storagePath(), result.outputFormat(), result.pageCount(), suffix);
    }

    private boolean isImageExt(String ext) {
        return switch (ext) {
            case "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff" -> true;
            default -> false;
        };
    }

    private String ext(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private String filename(String path) {
        return path.replace('\\', '/').substring(path.replace('\\', '/').lastIndexOf('/') + 1);
    }

    private String safeName(String name) {
        String safe = name == null || name.isBlank() ? "document" : name;
        return safe.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
