package com.danganguan.archive.document.process.boundary;

import java.util.List;

public record BoundaryGroup(
        List<Integer> imageIndexes,
        String reason
) {
}
