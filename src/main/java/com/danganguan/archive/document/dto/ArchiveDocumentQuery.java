package com.danganguan.archive.document.dto;

public record ArchiveDocumentQuery(
        Long hallId,
        Long taskId,
        String keyword,
        String folderName,
        String folderPath,
        Long tagId,
        String tagName,
        Integer page,
        Integer size
) {
}
