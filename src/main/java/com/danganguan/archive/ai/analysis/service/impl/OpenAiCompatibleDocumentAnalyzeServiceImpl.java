package com.danganguan.archive.ai.analysis.service.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.analysis.service.DocumentAnalyzeService;
import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.config.AiProviderProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.storage.FileStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@ConditionalOnProperty(prefix = "archive.ai", name = "provider", havingValue = "openai-compatible")
@RequiredArgsConstructor
public class OpenAiCompatibleDocumentAnalyzeServiceImpl implements DocumentAnalyzeService {
    private static final int TEXT_LIMIT = 8000;
    private static final int PDF_VISUAL_PAGE_LIMIT = 3;
    private static final int MIN_TEXT_LENGTH_BEFORE_VISION = 30;
    private static final int VISION_IMAGE_LIMIT = 6;
    private static final Pattern PERSON_PATTERN = Pattern.compile(
            "(?:作者姓名|学生姓名|姓名|申请人|负责人)[:：\\s]*"
                    + "([\\u4e00-\\u9fa5]{2,4}?)(?=专业|课程|学号|指导教师|导师|职称|所在单位|论文题目|硕士|博士|本科|申请材料|[，,。；;、]|$)"
    );

    private final AiProviderProperties properties;
    private final FileStorageService fileStorageService;
    private final OcrService ocrService;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Override
    public DocumentAnalyzeResult analyze(DocumentAnalyzeRequest request) {
        AiProviderProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        validateConfig(config);
        SourceAnalysis sourceAnalysis = analyzeSources(request);
        String content = callModel(config, request, sourceAnalysis);
        return parseResult(content, sourceAnalysis.text());
    }

    private void validateConfig(AiProviderProperties.OpenAiCompatible config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BizException("未配置外部 AI API Key，请设置 ARCHIVE_AI_API_KEY");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new BizException("未配置外部 AI 模型，请设置 ARCHIVE_AI_MODEL");
        }
    }

    private String callModel(AiProviderProperties.OpenAiCompatible config,
                             DocumentAnalyzeRequest request,
                             SourceAnalysis sourceAnalysis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(config.getTimeoutSeconds() * 1000);
        RestClient restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        Map<String, Object> payload = Map.of(
                "model", config.getModel(),
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", buildUserContent(request, sourceAnalysis))
                )
        );
        try {
            String requestUrl = chatCompletionsUrl(config.getBaseUrl());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            return extractMessageContent(response);
        } catch (Exception ex) {
            throw new BizException("外部 AI 文档分析失败：" + ex.getMessage() + "。请检查 base-url 是否为兼容 Chat Completions 的地址。");
        }
    }

    private String chatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException("未配置外部 AI 接口地址，请设置 ARCHIVE_AI_BASE_URL");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private List<Map<String, Object>> buildUserContent(DocumentAnalyzeRequest request, SourceAnalysis sourceAnalysis) {
        List<Map<String, Object>> content = new ArrayList<>();
        String extractedText = sourceAnalysis.text();
        boolean needVision = plainTextLength(extractedText) < MIN_TEXT_LENGTH_BEFORE_VISION;
        String text = """
                请分析这份档案材料，并只返回 JSON。
                原始文件名：%s
                输出文件路径：%s
                任务文件命名示例：%s
                任务文件夹命名示例：%s
                已提取文本或 OCR 文本：%s
                """.formatted(
                sourceNames(request),
                request.processedFile().storagePath(),
                blankToNone(request.task().getFileNameExample()),
                blankToNone(request.task().getFolderNameExample()),
                limit(extractedText, TEXT_LIMIT)
        );
        content.add(Map.of("type", "text", "text", text));
        if (needVision) {
            for (String imageDataUrl : sourceAnalysis.visionImageDataUrls().stream().limit(VISION_IMAGE_LIMIT).toList()) {
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", imageDataUrl)
                ));
            }
        }
        return content;
    }

    private String systemPrompt() {
        return """
                你是档案馆文档分析助手。请从档案图片或文本中提取用于命名和检索的信息。
                只返回一个 JSON 对象，不要 Markdown，不要解释。
                JSON 字段：
                extractedText: string，尽量还原可读正文；
                summary: string，80 字以内摘要；
                detectedPersonName: string|null，识别到的人名；
                keywords: string[]，适合做标签的关键词；
                confidence: number，0 到 1；
                reason: string，简短说明依据。
                """;
    }

    private SourceAnalysis analyzeSources(DocumentAnalyzeRequest request) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("archive-source-analyze-");
            List<SourceMaterial> materials = collectProcessedMaterial(request.processedFile().storagePath());
            if (materials.isEmpty()) {
                materials = collectMaterials(request.sourceFiles(), tempDir);
            }
            List<String> textPieces = new ArrayList<>();
            List<String> visionImages = new ArrayList<>();
            for (SourceMaterial material : materials) {
                if (material.uploadType() == UploadType.PDF) {
                    String text = extractPdfText(material.path());
                    if (text.isBlank()) {
                        List<Path> renderedPages = renderPdfPages(material.path(), tempDir);
                        textPieces.add(ocrImages(renderedPages));
                        visionImages.addAll(toImageDataUrls(renderedPages));
                    } else {
                        textPieces.add(text);
                    }
                } else if (material.uploadType() == UploadType.IMAGE) {
                    textPieces.add(ocrImage(material.path()));
                    visionImages.add(toImageDataUrl(material.path()));
                }
            }
            return new SourceAnalysis(String.join("\n", textPieces).trim(), visionImages);
        } catch (IOException ex) {
            throw new BizException("源文件分析失败：" + ex.getMessage());
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private List<SourceMaterial> collectProcessedMaterial(String storagePath) {
        Path processedPath = fileStorageService.resolve(storagePath);
        if (!Files.exists(processedPath)) {
            return List.of();
        }
        String ext = ext(processedPath.getFileName().toString());
        if ("pdf".equals(ext)) {
            return List.of(new SourceMaterial(UploadType.PDF, processedPath));
        }
        if (isImageExt(ext)) {
            return List.of(new SourceMaterial(UploadType.IMAGE, processedPath));
        }
        return List.of();
    }

    private List<SourceMaterial> collectMaterials(List<UploadedFile> sourceFiles, Path tempDir) throws IOException {
        List<SourceMaterial> materials = new ArrayList<>();
        for (UploadedFile file : sourceFiles) {
            Path source = fileStorageService.resolve(file.getStoragePath());
            if (file.getUploadType() == UploadType.PDF || file.getUploadType() == UploadType.IMAGE) {
                materials.add(new SourceMaterial(file.getUploadType(), source));
            } else if (file.getUploadType() == UploadType.ZIP) {
                materials.addAll(extractZipMaterials(source, tempDir));
            }
        }
        return materials;
    }

    private List<SourceMaterial> extractZipMaterials(Path zipPath, Path tempDir) throws IOException {
        try {
            return extractZipMaterialsWithCharset(zipPath, tempDir, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return extractZipMaterialsWithCharset(zipPath, tempDir, Charset.forName("GBK"));
        }
    }

    private List<SourceMaterial> extractZipMaterialsWithCharset(Path zipPath, Path tempDir, Charset charset) throws IOException {
        List<SourceMaterial> materials = new ArrayList<>();
        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(zipPath), charset)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String ext = ext(entry.getName());
                UploadType uploadType = uploadType(ext);
                if (uploadType == UploadType.UNKNOWN) {
                    continue;
                }
                Path target = tempDir.resolve(UUID.randomUUID() + "-" + Path.of(entry.getName()).getFileName()).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new BizException("压缩包内包含非法路径");
                }
                Files.copy(zipInput, target, StandardCopyOption.REPLACE_EXISTING);
                materials.add(new SourceMaterial(uploadType, target));
            }
        }
        return materials;
    }

    private String extractPdfText(Path pdfPath) {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return text == null ? "" : text.trim();
        } catch (IOException ex) {
            return "";
        }
    }

    private String ocrImages(List<Path> imagePaths) {
        List<String> pieces = new ArrayList<>();
        for (Path imagePath : imagePaths) {
            pieces.add(ocrImage(imagePath));
        }
        return String.join("\n", pieces).trim();
    }

    private String ocrImage(Path imagePath) {
        OcrResult result = ocrService.recognize(imagePath);
        return result.hasText() ? result.text() : "";
    }

    private List<Path> renderPdfPages(Path pdfPath, Path tempDir) {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), PDF_VISUAL_PAGE_LIMIT);
            List<Path> imagePaths = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
                Path target = tempDir.resolve("pdf-page-" + UUID.randomUUID() + "-" + pageIndex + ".png");
                ImageIO.write(image, "png", target.toFile());
                imagePaths.add(target);
            }
            return imagePaths;
        } catch (IOException ex) {
            throw new BizException("渲染待识别 PDF 页面失败：" + ex.getMessage());
        }
    }

    private List<String> toImageDataUrls(List<Path> imagePaths) {
        return imagePaths.stream().map(this::toImageDataUrl).toList();
    }

    private String toImageDataUrl(Path imagePath) {
        try {
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            return "data:" + imageMediaType(imagePath) + ";base64," + base64;
        } catch (IOException ex) {
            throw new BizException("读取待识别图片失败：" + ex.getMessage());
        }
    }

    private String extractMessageContent(Map<String, Object> response) {
        Object choicesValue = response == null ? null : response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new BizException("外部 AI 响应缺少 choices");
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new BizException("外部 AI 响应格式异常");
        }
        Object messageValue = choiceMap.get("message");
        if (!(messageValue instanceof Map<?, ?> messageMap)) {
            throw new BizException("外部 AI 响应缺少 message");
        }
        Object contentValue = messageMap.get("content");
        if (contentValue == null) {
            throw new BizException("外部 AI 响应缺少 content");
        }
        return contentValue.toString();
    }

    private DocumentAnalyzeResult parseResult(String content, String fallbackText) {
        if (content == null || content.isBlank()) {
            return fallbackResult(fallbackText, "外部 AI 返回空内容，已回退到 OCR/PDF 文本分析。");
        }
        try {
            Map<String, Object> result = objectMapper.readValue(cleanJson(content), new TypeReference<>() {});
            String extractedText = stringValue(result.get("extractedText"));
            String summary = stringValue(result.get("summary"));
            String detectedPersonName = stringValue(result.get("detectedPersonName"));
            List<String> keywords = stringList(result.get("keywords"));
            BigDecimal confidence = decimalValue(result.get("confidence"));
            String reason = stringValue(result.get("reason"));
            return new DocumentAnalyzeResult(
                    firstNonBlank(extractedText, fallbackText),
                    firstNonBlank(summary, "外部 AI 已完成文档分析。"),
                    detectedPersonName,
                    keywords,
                    confidence == null ? new BigDecimal("0.70") : confidence,
                    firstNonBlank(reason, "外部 AI 根据文档内容生成分析结果。")
            );
        } catch (JsonProcessingException ex) {
            return fallbackResult(fallbackText, "外部 AI 返回内容不是合法 JSON，已回退到 OCR/PDF 文本分析：" + ex.getMessage());
        }
    }

    private String cleanJson(String content) {
        String cleaned = content == null ? "" : content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 1);
        }
        return "";
    }

    private DocumentAnalyzeResult fallbackResult(String text, String reason) {
        String safeText = text == null ? "" : text.trim();
        String personName = detectPersonName(safeText);
        List<String> keywords = detectKeywords(safeText);
        String summary = safeText.isBlank()
                ? "外部 AI 未返回可解析内容，且 OCR/PDF 文本为空。"
                : "识别文本：" + limit(safeText.replaceAll("\\s+", " "), 180);
        return new DocumentAnalyzeResult(
                limit(safeText, TEXT_LIMIT),
                summary,
                personName,
                keywords,
                safeText.isBlank() ? new BigDecimal("0.20") : new BigDecimal("0.55"),
                reason
        );
    }

    private String detectPersonName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String compactText = text.replaceAll("\\s+", "");
        Matcher matcher = PERSON_PATTERN.matcher(compactText);
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

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = stringValue(item);
            if (text != null && !result.contains(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String sourceNames(DocumentAnalyzeRequest request) {
        return request.sourceFiles().stream()
                .map(UploadedFile::getOriginalName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private int plainTextLength(String text) {
        return text == null ? 0 : text.replaceAll("\\s+", "").length();
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

    private UploadType uploadType(String ext) {
        if ("pdf".equals(ext)) {
            return UploadType.PDF;
        }
        return switch (ext) {
            case "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff" -> UploadType.IMAGE;
            default -> UploadType.UNKNOWN;
        };
    }

    private boolean isImageExt(String ext) {
        return switch (ext.toLowerCase()) {
            case "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff" -> true;
            default -> false;
        };
    }

    private String imageMediaType(Path imagePath) {
        return switch (ext(imagePath.getFileName().toString())) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            default -> MediaType.IMAGE_PNG_VALUE;
        };
    }

    private String ext(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private record SourceAnalysis(String text, List<String> visionImageDataUrls) {
    }

    private record SourceMaterial(UploadType uploadType, Path path) {
    }
}
