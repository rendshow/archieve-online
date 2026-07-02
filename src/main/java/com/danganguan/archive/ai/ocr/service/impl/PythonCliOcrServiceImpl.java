package com.danganguan.archive.ai.ocr.service.impl;

import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.config.OcrProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "archive.ocr", name = "provider", havingValue = "python")
public class PythonCliOcrServiceImpl implements OcrService {
    private final OcrProperties properties;

    public PythonCliOcrServiceImpl(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public OcrResult recognize(Path imagePath) {
        List<String> command = List.of(
                properties.getPython().getCommand(),
                Path.of(properties.getPython().getScript()).toAbsolutePath().toString(),
                imagePath.toAbsolutePath().toString(),
                properties.getLanguages()
        );
        return run(command);
    }

    private OcrResult run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!finished) {
                process.destroyForcibly();
                return new OcrResult("", BigDecimal.ZERO, "python", "OCR 超时");
            }
            if (process.exitValue() != 0) {
                return new OcrResult("", BigDecimal.ZERO, "python", output);
            }
            return new OcrResult(output, output.isBlank() ? BigDecimal.ZERO : new BigDecimal("0.60"), "python", "Python OCR 完成");
        } catch (Exception ex) {
            return new OcrResult("", BigDecimal.ZERO, "python", ex.getMessage());
        }
    }
}
