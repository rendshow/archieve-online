package com.danganguan.archive.ai.analysis.dto;

import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;

import java.util.List;

public record DocumentAnalyzeRequest(
        ArchiveTask task,
        List<UploadedFile> sourceFiles,
        ProcessedFileResult processedFile
) {
    public DocumentAnalyzeRequest(ArchiveTask task, UploadedFile sourceFile, ProcessedFileResult processedFile) {
        this(task, List.of(sourceFile), processedFile);
    }
}
