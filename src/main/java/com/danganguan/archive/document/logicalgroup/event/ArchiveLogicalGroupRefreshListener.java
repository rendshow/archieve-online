package com.danganguan.archive.document.logicalgroup.event;

import com.danganguan.archive.document.logicalgroup.service.ArchiveLogicalGroupService;
import com.danganguan.archive.event.ArchiveRealtimeEvent;
import com.danganguan.archive.event.ArchiveRealtimeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArchiveLogicalGroupRefreshListener {
    private final ArchiveLogicalGroupService archiveLogicalGroupService;
    private final ArchiveRealtimeEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void refresh(ArchiveLogicalGroupRefreshRequested event) {
        if (event.hallId() == null || event.folderPaths().isEmpty()) {
            return;
        }
        try {
            archiveLogicalGroupService.rebuildFolders(event.hallId(), event.folderPaths());
            eventPublisher.publish(ArchiveRealtimeEvent.archiveDocumentsChanged(
                    event.hallId(),
                    java.util.List.of(),
                    "逻辑档案组已更新"
            ));
        } catch (RuntimeException ex) {
            log.warn("逻辑档案组重建失败，hallId={}, reason={}", event.hallId(), event.reason(), ex);
            eventPublisher.publish(ArchiveRealtimeEvent.archiveDocumentsChanged(
                    event.hallId(),
                    java.util.List.of(),
                    "档案已更新，但逻辑档案组暂未重建"
            ));
        }
    }
}
