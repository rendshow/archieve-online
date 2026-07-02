package com.danganguan.archive.ai.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

public record DocumentAnalyzeResult(
        String extractedText,
        String summary,
        String detectedPersonName,
        List<String> keywords,
        BigDecimal confidence,
        String reason
) {
    public List<String> keywords() {
        return keywords == null ? List.of() : keywords;
    }
}
