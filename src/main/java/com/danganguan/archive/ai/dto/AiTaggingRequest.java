package com.danganguan.archive.ai.dto;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;

public record AiTaggingRequest(ArchiveTask task, UploadedFile file, String suggestedName, DocumentAnalyzeResult analyzeResult) {
}
