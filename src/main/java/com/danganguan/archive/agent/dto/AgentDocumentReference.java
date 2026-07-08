package com.danganguan.archive.agent.dto;

public record AgentDocumentReference(
        Long documentId,
        Long hallId,
        String title,
        String folderPath,
        String fileFormat
) {
}
