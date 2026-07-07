package com.danganguan.archive.document.folder.dto;

public record MoveArchiveFolderResult(
        Long hallId,
        String sourceFolderPath,
        String targetFolderPath,
        int movedCount
) {
}
