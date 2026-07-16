package com.danganguan.archive.document.logicalgroup.dto;

public record ArchiveLogicalGroupRebuildResult(
        Long hallId,
        String folderPath,
        int sourceDocumentCount,
        int groupCount,
        int reviewRequiredCount
) {
}
