package com.danganguan.archive.document.legacygrouping.service;

import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreview;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreviewRequest;

public interface LegacyArchiveGroupingService {
    LegacyArchiveGroupingPreview preview(LegacyArchiveGroupingPreviewRequest request);
}
