package com.danganguan.archive.document.process;

import com.danganguan.archive.task.enums.OutputFormat;

public record ProcessedFileResult(
        String storagePath,
        OutputFormat outputFormat,
        Integer pageCount,
        String nameSuffix
) {
}
