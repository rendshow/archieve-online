package com.danganguan.archive.document.process.boundary;

import java.math.BigDecimal;
import java.util.List;

public record BoundaryDetectionResult(
        List<BoundaryGroup> groups,
        BigDecimal confidence,
        String reason
) {
}
