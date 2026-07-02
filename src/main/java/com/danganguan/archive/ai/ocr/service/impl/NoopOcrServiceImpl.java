package com.danganguan.archive.ai.ocr.service.impl;

import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "archive.ocr", name = "provider", havingValue = "none", matchIfMissing = true)
public class NoopOcrServiceImpl implements OcrService {
    @Override
    public OcrResult recognize(Path imagePath) {
        return new OcrResult("", BigDecimal.ZERO, "none", "未启用本地 OCR");
    }
}
