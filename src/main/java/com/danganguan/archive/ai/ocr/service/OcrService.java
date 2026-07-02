package com.danganguan.archive.ai.ocr.service;

import com.danganguan.archive.ai.ocr.dto.OcrResult;

import java.nio.file.Path;

public interface OcrService {
    OcrResult recognize(Path imagePath);
}
