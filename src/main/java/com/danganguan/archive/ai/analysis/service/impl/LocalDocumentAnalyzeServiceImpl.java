package com.danganguan.archive.ai.analysis.service.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.analysis.service.DocumentAnalyzeService;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.task.enums.OutputFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "archive.ai", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalDocumentAnalyzeServiceImpl implements DocumentAnalyzeService {
    private static final Pattern PERSON_PATTERN = Pattern.compile("(?:姓名|学生姓名|申请人|负责人)[:：\\s]*([\\u4e00-\\u9fa5]{2,4})");
    private static final int TEXT_LIMIT = 8000;

    private final FileStorageService fileStorageService;

    public LocalDocumentAnalyzeServiceImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public DocumentAnalyzeResult analyze(DocumentAnalyzeRequest request) {
        String extractedText = extractText(request);
        String fallbackText = firstNonBlank(extractedText, request.sourceFile().getOriginalName(), request.processedFile().storagePath());
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
        if (request.processedFile().outputFormat() != OutputFormat.PDF) {
            return "";
        }
        Path pdfPath = fileStorageService.resolve(request.processedFile().storagePath());
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return text == null ? "" : text.trim();
        } catch (IOException ex) {
            throw new BizException("PDF 文本提取失败：" + ex.getMessage());
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
        summary.append("。来源文件：").append(request.sourceFile().getOriginalName());
        return summary.toString();
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
