package com.danganguan.archive.document.indexing.dto;

public record CreateArchiveTextIndexJobRequest(
        Long hallId,
        Integer batchSize
) {
}
