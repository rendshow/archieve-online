package com.danganguan.archive.document.indexing.dto;

import com.danganguan.archive.document.indexing.enums.ArchiveTextIndexMode;

import java.util.List;

public record CreateArchiveTextIndexJobRequest(
        Long hallId,
        Integer batchSize,
        ArchiveTextIndexMode mode,
        List<Long> documentIds
) {
}
