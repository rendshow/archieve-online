package com.danganguan.archive.search.dto;

public record ArchivePageSearchHit(
        Long documentId,
        Long hallId,
        String title,
        String folderPath,
        Integer pageNo,
        String ocrText,
        double score
) {
}
