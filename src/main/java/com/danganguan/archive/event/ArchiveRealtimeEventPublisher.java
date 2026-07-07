package com.danganguan.archive.event;

import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.workspace.entity.WorkspaceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
public class ArchiveRealtimeEventPublisher {
    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, ArchiveRealtimeEvent.connected());
        return emitter;
    }

    public void publish(ArchiveRealtimeEvent event) {
        for (SseEmitter emitter : emitters) {
            if (!send(emitter, event)) {
                emitters.remove(emitter);
            }
        }
    }

    public void sourceFilesChanged(Long taskId, Long hallId, List<UploadedFile> files, String status, String message) {
        publish(ArchiveRealtimeEvent.sourceFilesChanged(
                taskId,
                hallId,
                files == null ? List.of() : files.stream().map(UploadedFile::getId).toList(),
                status,
                message
        ));
    }

    public void workspaceDocumentsChanged(Long taskId, Long hallId, List<WorkspaceDocument> documents,
                                          String status, String message) {
        publish(ArchiveRealtimeEvent.workspaceDocumentsChanged(
                taskId,
                hallId,
                documents == null ? List.of() : documents.stream().map(WorkspaceDocument::getId).toList(),
                status,
                message
        ));
    }

    private boolean send(SseEmitter emitter, ArchiveRealtimeEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException ex) {
            return false;
        }
    }
}
