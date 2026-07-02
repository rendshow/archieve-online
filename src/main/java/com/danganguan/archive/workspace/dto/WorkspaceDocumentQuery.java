package com.danganguan.archive.workspace.dto;

public record WorkspaceDocumentQuery(
        Long hallId,
        Long taskId,
        String keyword,
        String folderName,
        Long tagId,
        String tagName,
        Integer page,
        Integer size
) {
}
