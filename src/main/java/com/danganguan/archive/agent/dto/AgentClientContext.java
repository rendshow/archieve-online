package com.danganguan.archive.agent.dto;

import java.util.List;

public record AgentClientContext(
        String pageType,
        Long hallId,
        String folderPath,
        Long documentId,
        Long taskId,
        List<Long> selectedDocumentIds,
        String searchKeyword
) {
}
