package com.danganguan.archive.document.legacygrouping.dto;

import java.util.List;

public record LegacyArchiveGroupingPreview(
        String configuredRoot,
        String relativeFolderPath,
        int supportedFileCount,
        int unsupportedFileCount,
        int personRecordCount,
        int catalogGroupCount,
        int unknownFileCount,
        int reviewRequiredCount,
        List<LegacyArchiveGroupPreview> groups
) {
}
