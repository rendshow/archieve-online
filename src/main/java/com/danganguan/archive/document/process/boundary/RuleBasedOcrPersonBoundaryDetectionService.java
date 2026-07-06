package com.danganguan.archive.document.process.boundary;

import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RuleBasedOcrPersonBoundaryDetectionService implements PersonBoundaryDetectionService {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");

    private final OcrService ocrService;

    @Override
    public List<BoundaryGroup> detect(List<BoundaryImage> images) {
        if (images == null || images.isEmpty()) {
            throw new BizException("AI 边界拆分时没有可分析的图片");
        }
        List<BoundaryGroup> groups = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (BoundaryImage image : images) {
            OcrResult ocrResult = ocrService.recognize(image.imagePath());
            String text = normalizeText(ocrResult.text());
            boolean startPage = isLikelyPersonStartPage(text);
            if (!current.isEmpty() && startPage) {
                groups.add(new BoundaryGroup(List.copyOf(current), "OCR 识别到新的人员首页"));
                current = new ArrayList<>();
            }
            current.add(image.index());
        }
        if (!current.isEmpty()) {
            groups.add(new BoundaryGroup(List.copyOf(current), "尾部人员材料"));
        }
        if (groups.isEmpty()) {
            throw new BizException("AI 边界拆分未能生成有效分组");
        }
        return groups;
    }

    private boolean isLikelyPersonStartPage(String text) {
        if (text.isBlank()) {
            return false;
        }
        int score = 0;
        if (containsAny(text, "研究生毕业鉴定表", "毕业鉴定表")) {
            score += 3;
        }
        if (text.contains("姓名")) {
            score += 2;
        }
        if (containsAny(text, "工作单位", "学位级别", "所学专业", "指导教师")) {
            score += 2;
        }
        if (containsAny(text, "个人鉴定", "本人鉴定")) {
            score += 2;
        }
        if (containsAny(text, "学位评定委员会决议", "指导教师鉴定意见", "部系所审核意见", "学校审核意见")) {
            score -= 3;
        }
        return score >= 5;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return ANSI_PATTERN.matcher(text)
                .replaceAll("")
                .replaceAll("\\s+", "");
    }
}
