package com.danganguan.archive.document.dto;

public record ArchiveDocumentQuery(
        Long hallId,
        Long taskId,
        String keyword,
        String folderName,
        Long tagId,
        Integer page,
        Integer size
) {
}
