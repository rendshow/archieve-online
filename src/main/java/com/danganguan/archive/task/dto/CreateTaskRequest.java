package com.danganguan.archive.task.dto;

import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;

public record CreateTaskRequest(
        Long hallId,
        String taskName,
        String namingSource,
        String folderNameExample,
        String fileNameExample,
        Boolean allowAiOverride,
        Boolean enableScanEnhance,
        PersonSplitStrategy personSplitStrategy,
        Integer fixedElementsPerPerson,
        OutputFormat outputFormat
) {
}
