package com.danganguan.archive.document.legacygrouping.dto;

public record LegacyArchiveFilePreview(
        String fileName,
        String relativePath,
        String extension,
        long size,
        Integer sequenceNo
) {
}
