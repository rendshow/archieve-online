package com.danganguan.archive.ai.analysis.service.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.analysis.service.DocumentAnalyzeService;
import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.storage.FileStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@ConditionalOnProperty(prefix = "archive.ai", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalDocumentAnalyzeServiceImpl implements DocumentAnalyzeService {
    private static final Pattern PERSON_PATTERN = Pattern.compile("(?:姓名|学生姓名|申请人|负责人)[:：\\s]*([\\u4e00-\\u9fa5]{2,4})");
    private static final int TEXT_LIMIT = 8000;
    private static final int PDF_OCR_PAGE_LIMIT = 3;

    private final FileStorageService fileStorageService;
    private final OcrService ocrService;

    public LocalDocumentAnalyzeServiceImpl(FileStorageService fileStorageService, OcrService ocrService) {
        this.fileStorageService = fileStorageService;
        this.ocrService = ocrService;
    }

    @Override
    public DocumentAnalyzeResult analyze(DocumentAnalyzeRequest request) {
        String extractedText = extractText(request);
        String fallbackText = firstNonBlank(extractedText, sourceNames(request), request.processedFile().storagePath());
        String personName = detectPersonName(fallbackText);
        List<String> keywords = detectKeywords(fallbackText);
        String summary = buildSummary(request, extractedText, personName, keywords);
        BigDecimal confidence = extractedText == null || extractedText.isBlank()
                ? new BigDecimal("0.35")
                : new BigDecimal("0.70");
        String reason = extractedText == null || extractedText.isBlank()
                ? "本地分析未提取到正文文本，暂按文件名和任务上下文生成分析结果。"
                : "本地分析已从 PDF 文本层提取正文，并基于关键词生成分析结果。";
        return new DocumentAnalyzeResult(limit(extractedText, TEXT_LIMIT), summary, personName, keywords, confidence, reason);
    }

    private String extractText(DocumentAnalyzeRequest request) {
        List<String> pieces = new ArrayList<>();
        for (UploadedFile file : request.sourceFiles()) {
            Path source = fileStorageService.resolve(file.getStoragePath());
            if (file.getUploadType() == UploadType.PDF) {
                String pdfText = extractPdfText(source);
                if (pdfText.isBlank()) {
                    pieces.add(renderPdfAndOcr(source));
                } else {
                    pieces.add(pdfText);
                }
            } else if (file.getUploadType() == UploadType.IMAGE) {
                pieces.add(ocrImage(source));
            } else if (file.getUploadType() == UploadType.ZIP) {
                pieces.add(extractZipText(source));
            }
        }
        return String.join("\n", pieces).trim();
    }

    private String extractPdfText(Path pdfPath) {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return text == null ? "" : text.trim();
        } catch (IOException ex) {
            throw new BizException("PDF 文本提取失败：" + ex.getMessage());
        }
    }

    private String renderPdfAndOcr(Path pdfPath) {
        Path tempDir = null;
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            tempDir = Files.createTempDirectory("archive-pdf-ocr-");
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), PDF_OCR_PAGE_LIMIT);
            List<String> pieces = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
                Path target = tempDir.resolve("page-" + pageIndex + ".png");
                ImageIO.write(image, "png", target.toFile());
                pieces.add(ocrImage(target));
            }
            return String.join("\n", pieces).trim();
        } catch (IOException ex) {
            throw new BizException("扫描 PDF OCR 失败：" + ex.getMessage());
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private String ocrImage(Path imagePath) {
        OcrResult result = ocrService.recognize(imagePath);
        return result.hasText() ? result.text() : "";
    }

    private String extractZipText(Path zipPath) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("archive-zip-ocr-");
            List<String> pieces = new ArrayList<>();
            try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String filename = entry.getName().replace('\\', '/');
                    String ext = ext(filename);
                    if (!isSupportedExt(ext)) {
                        continue;
                    }
                    Path target = tempDir.resolve(UUID.randomUUID() + "-" + Path.of(filename).getFileName()).normalize();
                    if (!target.startsWith(tempDir)) {
                        throw new BizException("压缩包内包含非法路径");
                    }
                    Files.copy(zipInput, target);
                    if ("pdf".equals(ext)) {
                        String text = extractPdfText(target);
                        pieces.add(text.isBlank() ? renderPdfAndOcr(target) : text);
                    } else {
                        pieces.add(ocrImage(target));
                    }
                }
            }
            return String.join("\n", pieces).trim();
        } catch (IOException ex) {
            throw new BizException("压缩包 OCR 失败：" + ex.getMessage());
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private String detectPersonName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = PERSON_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> detectKeywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        String source = text == null ? "" : text;
        addIfContains(keywords, source, "硕士");
        addIfContains(keywords, source, "博士");
        addIfContains(keywords, source, "本科");
        addIfContains(keywords, source, "财务");
        addIfContains(keywords, source, "奖学金");
        addIfContains(keywords, source, "毕业");
        addIfContains(keywords, source, "学籍");
        addIfContains(keywords, source, "合同");
        addIfContains(keywords, source, "证明");
        addIfContains(keywords, source, "申请");
        return new ArrayList<>(keywords);
    }

    private void addIfContains(Set<String> keywords, String source, String keyword) {
        if (source.contains(keyword)) {
            keywords.add(keyword);
        }
    }

    private String buildSummary(DocumentAnalyzeRequest request, String extractedText, String personName, List<String> keywords) {
        if (extractedText != null && !extractedText.isBlank()) {
            String prefix = limit(extractedText.replaceAll("\\s+", " "), 180);
            return "识别文本：" + prefix;
        }
        StringBuilder summary = new StringBuilder("本地分析暂未获取正文文本");
        if (personName != null && !personName.isBlank()) {
            summary.append("，疑似姓名：").append(personName);
        }
        if (!keywords.isEmpty()) {
            summary.append("，关键词：").append(String.join("、", keywords));
        }
        summary.append("。来源文件：").append(sourceNames(request));
        return summary.toString();
    }

    private String sourceNames(DocumentAnalyzeRequest request) {
        return request.sourceFiles().stream()
                .map(UploadedFile::getOriginalName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private void deleteTempDir(Path tempDir) {
        if (tempDir == null) {
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

    private boolean isSupportedExt(String ext) {
        return "pdf".equals(ext) || switch (ext) {
            case "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff" -> true;
            default -> false;
        };
    }

    private String ext(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
