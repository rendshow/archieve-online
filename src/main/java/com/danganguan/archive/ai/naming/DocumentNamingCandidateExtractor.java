package com.danganguan.archive.ai.naming;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.dto.AiNamingRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentNamingCandidateExtractor {
    private static final int TEXT_SNIPPET_LIMIT = 600;
    private static final Pattern STUDENT_NO = Pattern.compile("(?:学号|考号|准考证号|学生证号)[:：\\s]*([A-Za-z]?\\d{6,20})");

    public DocumentNamingCandidate extract(AiNamingRequest request) {
        DocumentAnalyzeResult analyzeResult = request.analyzeResult();
        String text = analyzeResult == null ? "" : nullToBlank(analyzeResult.extractedText());
        List<String> keywords = analyzeResult == null ? List.of() : analyzeResult.keywords();
        return new DocumentNamingCandidate(
                analyzeResult == null ? null : analyzeResult.detectedPersonName(),
                detectStudentNo(text),
                detectMaterialType(text, keywords),
                mergeKeywords(text, keywords),
                request.file().getOriginalName(),
                limit(text.replaceAll("\\s+", " "), TEXT_SNIPPET_LIMIT)
        );
    }

    private String detectStudentNo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = STUDENT_NO.matcher(text.replaceAll("\\s+", ""));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String detectMaterialType(String text, List<String> keywords) {
        String source = nullToBlank(text) + " " + String.join(" ", keywords == null ? List.of() : keywords);
        for (String candidate : List.of("学籍", "学位", "毕业", "报到证", "财务", "证明", "申请", "合同", "奖学金")) {
            if (source.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> mergeKeywords(String text, List<String> keywords) {
        Set<String> result = new LinkedHashSet<>();
        if (keywords != null) {
            result.addAll(keywords);
        }
        String source = nullToBlank(text);
        for (String candidate : List.of("硕士", "博士", "本科", "财务", "奖学金", "毕业", "学籍", "学位", "报到证", "合同", "证明", "申请")) {
            if (source.contains(candidate)) {
                result.add(candidate);
            }
        }
        return new ArrayList<>(result);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }
}
