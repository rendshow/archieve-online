package com.danganguan.archive.document.process;

import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;

import java.util.List;

public interface DocumentProcessingService {
    List<ProcessedFileResult> process(ArchiveTask task, UploadedFile file);

    List<ProcessedFileResult> processGroup(ArchiveTask task, List<UploadedFile> files);
}
