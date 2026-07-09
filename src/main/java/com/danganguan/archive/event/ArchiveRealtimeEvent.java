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
                null, "SSE connected", LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent taskChanged(Long taskId, Long hallId, String status, String message) {
        return new ArchiveRealtimeEvent("TASK_CHANGED", taskId, hallId, null, null, List.of(), List.of(), List.of(),
                status, message, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent sourceFilesChanged(Long taskId, Long hallId, List<Long> sourceFileIds,
                                                          String status, String message) {
        return new ArchiveRealtimeEvent("SOURCE_FILES_CHANGED", taskId, hallId, null, null, sourceFileIds, List.of(), List.of(),
                status, message, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent workspaceDocumentsChanged(Long taskId, Long hallId, List<Long> workspaceDocumentIds,
                                                                 String status, String message) {
        return new ArchiveRealtimeEvent("WORKSPACE_DOCUMENTS_CHANGED", taskId, hallId, null, null, List.of(),
                workspaceDocumentIds, List.of(), status, message, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent importJobChanged(Long importJobId, Long hallId, String status, String message) {
        return new ArchiveRealtimeEvent("FINISHED_IMPORT_JOB_CHANGED", null, hallId, importJobId, null, List.of(), List.of(),
                List.of(), status, message, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent archiveDocumentsChanged(Long hallId, List<Long> archiveDocumentIds,
                                                               String message) {
        return new ArchiveRealtimeEvent("ARCHIVE_DOCUMENTS_CHANGED", null, hallId, null, null, List.of(), List.of(),
                archiveDocumentIds, null, message, LocalDateTime.now());
    }

    public static ArchiveRealtimeEvent textIndexJobChanged(Long textIndexJobId, Long hallId, String status, String message) {
        return new ArchiveRealtimeEvent("TEXT_INDEX_JOB_CHANGED", null, hallId, null, textIndexJobId,
                List.of(), List.of(), List.of(), status, message, LocalDateTime.now());
    }
}
