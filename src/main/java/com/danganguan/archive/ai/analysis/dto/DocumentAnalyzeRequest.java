package com.danganguan.archive.ai.analysis.dto;

import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;

public record DocumentAnalyzeRequest(
        ArchiveTask task,
        UploadedFile sourceFile,
        ProcessedFileResult processedFile
) {
}
