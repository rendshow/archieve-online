package com.danganguan.archive.ai.ocr.service.impl;

import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.ocr", name = "provider", havingValue = "rapidocr")
public class RapidOcrCliServiceImpl implements OcrService {
    private final OcrProperties properties;

    @Override
    public OcrResult recognize(Path imagePath) {
        List<String> command = List.of(
                properties.getRapidocr().getCommand(),
                Path.of(properties.getRapidocr().getScript()).toAbsolutePath().toString(),
                imagePath.toAbsolutePath().toString()
        );
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new OcrResult("", BigDecimal.ZERO, "rapidocr", "OCR 超时");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                return new OcrResult("", BigDecimal.ZERO, "rapidocr", firstNonBlank(error, output));
            }
            return new OcrResult(output, output.isBlank() ? BigDecimal.ZERO : new BigDecimal("0.85"), "rapidocr", "RapidOCR 完成");
        } catch (Exception ex) {
            return new OcrResult("", BigDecimal.ZERO, "rapidocr", ex.getMessage());
        }
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
