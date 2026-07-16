package com.danganguan.archive.document.folder.dto;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupSummary;

import java.util.List;

public record ArchiveFolderChildren(
        Long hallId,
        String folderPath,
        List<ArchiveFolderNode> folders,
        List<ArchiveDocument> documents,
        List<ArchiveLogicalGroupSummary> logicalGroups
) {
}
