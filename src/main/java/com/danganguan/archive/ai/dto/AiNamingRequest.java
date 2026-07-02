package com.danganguan.archive.ai.dto;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;

public record AiNamingRequest(ArchiveTask task, UploadedFile file, DocumentAnalyzeResult analyzeResult) {
}
