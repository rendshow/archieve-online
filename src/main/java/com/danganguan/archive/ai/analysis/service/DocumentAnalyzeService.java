package com.danganguan.archive.ai.analysis.service;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;

public interface DocumentAnalyzeService {
    DocumentAnalyzeResult analyze(DocumentAnalyzeRequest request);
}
