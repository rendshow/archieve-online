package com.danganguan.archive.document.logicalgroup.event;

import java.util.LinkedHashSet;
import java.util.Set;

public record ArchiveLogicalGroupRefreshRequested(Long hallId, Set<String> folderPaths, String reason) {
    public ArchiveLogicalGroupRefreshRequested {
        folderPaths = folderPaths == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(folderPaths));
    }
}
