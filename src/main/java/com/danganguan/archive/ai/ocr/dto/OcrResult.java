package com.danganguan.archive.ai.ocr.dto;

import java.math.BigDecimal;

public record OcrResult(String text, BigDecimal confidence, String engine, String reason) {
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
