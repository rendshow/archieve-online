package com.danganguan.archive.document.legacygrouping.dto;

import com.danganguan.archive.document.legacygrouping.enums.LegacyArchiveGroupType;
import com.danganguan.archive.document.legacygrouping.enums.LegacyArchiveGroupingConfidence;

import java.util.List;

public record LegacyArchiveGroupPreview(
        String groupKey,
        LegacyArchiveGroupType groupType,
        String personNameCandidate,
        String archiveNoCandidate,
        LegacyArchiveGroupingConfidence confidence,
        String groupingRule,
        boolean requiresReview,
        List<LegacyArchiveFilePreview> files
) {
}
