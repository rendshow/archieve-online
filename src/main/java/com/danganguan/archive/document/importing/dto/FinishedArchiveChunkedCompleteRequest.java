package com.danganguan.archive.document.importing.dto;

import java.util.List;

public record FinishedArchiveChunkedCompleteRequest(
        List<FileManifest> files
) {
    public record FileManifest(
            Integer fileIndex,
            String relativePath,
            Integer totalChunks,
            Long fileSize
    ) {
    }
}
