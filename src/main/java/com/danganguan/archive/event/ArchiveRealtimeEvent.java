package com.danganguan.archive.event;

import java.time.LocalDateTime;
import java.util.List;

public record ArchiveRealtimeEvent(
        String type,
        Long taskId,
        Long hallId,
        Long importJobId,
        Long textIndexJobId,
        List<Long> sourceFileIds,
        List<Long> workspaceDocumentIds,
        List<Long> archiveDocumentIds,
        String status,
        String message,
        Integer totalCount,
        Integer processedCount,
        Integer successCount,
        Integer skippedCount,
        Integer failedCount,
        LocalDateTime occurredAt
) {
    public ArchiveRealtimeEvent {
        sourceFileIds = sourceFileIds == null ? List.of() : sourceFileIds;
        workspaceDocumentIds = workspaceDocumentIds == null ? List.of() : workspaceDocumentIds;
        archiveDocumentIds = archiveDocumentIds == null ? List.of() : archiveDocumentIds;
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
    }

    public static ArchiveRealtimeEvent connected() {
        return new ArchiveRealtimeEvent("CONNECTED", null, null, null, null, List.of(), List.of(), List.of(),
                null, "SSE connected", null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent taskChanged(Long taskId, Long hallId, String status, String message) {
        return new ArchiveRealtimeEvent("TASK_CHANGED", taskId, hallId, null, null, List.of(), List.of(), List.of(),
                status, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent sourceFilesChanged(Long taskId, Long hallId, List<Long> sourceFileIds,
                                                          String status, String message) {
        return new ArchiveRealtimeEvent("SOURCE_FILES_CHANGED", taskId, hallId, null, null, sourceFileIds, List.of(), List.of(),
                status, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent workspaceDocumentsChanged(Long taskId, Long hallId, List<Long> workspaceDocumentIds,
                                                                 String status, String message) {
        return new ArchiveRealtimeEvent("WORKSPACE_DOCUMENTS_CHANGED", taskId, hallId, null, null, List.of(),
                workspaceDocumentIds, List.of(), status, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent importJobChanged(Long importJobId, Long hallId, String status, String message) {
        return new ArchiveRealtimeEvent("FINISHED_IMPORT_JOB_CHANGED", null, hallId, importJobId, null, List.of(), List.of(),
                List.of(), status, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent importJobChanged(Long importJobId, Long hallId, String status, String message,
                                                        Integer totalCount, Integer importedCount, Integer skippedCount) {
        return new ArchiveRealtimeEvent("FINISHED_IMPORT_JOB_CHANGED", null, hallId, importJobId, null, List.of(), List.of(),
                List.of(), status, message, totalCount,
                sum(importedCount, skippedCount), importedCount, skippedCount, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent archiveDocumentsChanged(Long hallId, List<Long> archiveDocumentIds,
                                                               String message) {
        return new ArchiveRealtimeEvent("ARCHIVE_DOCUMENTS_CHANGED", null, hallId, null, null, List.of(), List.of(),
                archiveDocumentIds, null, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent textIndexJobChanged(Long textIndexJobId, Long hallId, String status, String message) {
        return new ArchiveRealtimeEvent("TEXT_INDEX_JOB_CHANGED", null, hallId, null, textIndexJobId,
                List.of(), List.of(), List.of(), status, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent textIndexJobChanged(Long textIndexJobId, Long hallId, String status, String message,
                                                           Integer totalCount, Integer processedCount, Integer successCount,
                                                           Integer skippedCount, Integer failedCount) {
        return new ArchiveRealtimeEvent("TEXT_INDEX_JOB_CHANGED", null, hallId, null, textIndexJobId,
                List.of(), List.of(), List.of(), status, message, totalCount, processedCount, successCount,
                skippedCount, failedCount, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent archiveDocumentIndexed(Long hallId, Long archiveDocumentId, String message) {
        return new ArchiveRealtimeEvent("ARCHIVE_DOCUMENT_INDEXED", null, hallId, null, null,
                List.of(), List.of(), List.of(archiveDocumentId), null, message,
                null, null, null, null, null, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent agentKnowledgeChanged(Long hallId, List<Long> archiveDocumentIds, String message) {
        return new ArchiveRealtimeEvent("AGENT_KNOWLEDGE_CHANGED", null, hallId, null, null,
                List.of(), List.of(), archiveDocumentIds, null, message,
                null, null, null, null, null, LocalDateTime.now());
    }

    private static Integer sum(Integer first, Integer second) {
        if (first == null && second == null) {
            return null;
        }
        return (first == null ? 0 : first) + (second == null ? 0 : second);
    }
}
