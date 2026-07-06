package com.danganguan.archive.document.process.boundary;

import java.nio.file.Path;

public record BoundaryImage(
        int index,
        int order,
        String entryName,
        Path imagePath
) {
}
