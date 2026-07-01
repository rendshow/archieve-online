package com.danganguan.archive.task.dto;

import com.danganguan.archive.task.enums.ConvertStrategy;
import com.danganguan.archive.task.enums.OutputFormat;

public record CreateTaskRequest(
        Long hallId,
        String taskName,
        String namingSource,
        String folderNameExample,
        String fileNameExample,
        Boolean allowAiOverride,
        Boolean enableScanEnhance,
        ConvertStrategy convertStrategy,
        Integer fixedSplitCount,
        OutputFormat outputFormat
) {
}
