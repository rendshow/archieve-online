package com.danganguan.archive.document.importing.dto;

public record FinishedArchiveChunkUploadResult(
        Long jobId,
        Integer fileIndex,
        Integer chunkIndex,
        Integer totalChunks,
        Boolean completed
) {
}
