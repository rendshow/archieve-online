package com.danganguan.archive.document.logicalgroup.dto;

import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupConfidence;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupType;

public record ArchiveLogicalGroupSummary(
        Long id,
        String title,
        String personName,
        String archiveNo,
        ArchiveLogicalGroupType groupType,
        ArchiveLogicalGroupConfidence confidence,
        String groupingRule,
        Boolean requiresReview,
        int memberCount
) {
}
