package com.danganguan.archive.document.logicalgroup.dto;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.logicalgroup.entity.ArchiveLogicalGroup;

import java.util.List;

public record ArchiveLogicalGroupDetail(
        ArchiveLogicalGroup group,
        List<ArchiveDocument> documents
) {
}
