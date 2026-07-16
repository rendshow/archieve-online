package com.danganguan.archive.document.logicalgroup.rule;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupConfidence;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupType;

import java.util.List;

public record ArchiveLogicalGroupCandidate(
        String groupKey,
        ArchiveLogicalGroupType groupType,
        String title,
        String personName,
        String archiveNo,
        ArchiveLogicalGroupConfidence confidence,
        String groupingRule,
        boolean requiresReview,
        List<ArchiveDocument> documents
) {
}
