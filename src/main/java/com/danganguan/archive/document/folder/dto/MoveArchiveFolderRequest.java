package com.danganguan.archive.document.folder.dto;

public record MoveArchiveFolderRequest(
        Long hallId,
        String sourceFolderPath,
        String targetParentFolderPath
) {
}
