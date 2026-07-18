package com.danganguan.archive.document.indexing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.document.indexing.entity.ArchiveDocumentIndexState;
import com.danganguan.archive.document.indexing.enums.ArchiveDocumentIndexStatus;
import com.danganguan.archive.document.indexing.mapper.ArchiveDocumentIndexStateMapper;
import com.danganguan.archive.document.indexing.service.ArchiveDocumentIndexStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentIndexStateServiceImpl implements ArchiveDocumentIndexStateService {
    private final ArchiveDocumentIndexStateMapper stateMapper;

    @Override
    public void mark(Long documentId, ArchiveDocumentIndexStatus status, String errorMessage) {
        if (documentId == null) {
            return;
        }
        ArchiveDocumentIndexState state = stateMapper.selectOne(new LambdaQueryWrapper<ArchiveDocumentIndexState>()
                .eq(ArchiveDocumentIndexState::getDocumentId, documentId));
        LocalDateTime now = LocalDateTime.now();
        if (state == null) {
            state = new ArchiveDocumentIndexState();
            state.setDocumentId(documentId);
            state.setAttemptCount(0);
        }
        if (status == ArchiveDocumentIndexStatus.OCR_RUNNING) {
            state.setAttemptCount((state.getAttemptCount() == null ? 0 : state.getAttemptCount()) + 1);
        }
        state.setStatus(status);
        state.setIndexVersion("v1");
        state.setLastError(errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 1000)));
        state.setUpdatedAt(now);
        if (status == ArchiveDocumentIndexStatus.READY || status == ArchiveDocumentIndexStatus.PARTIAL) {
            state.setIndexedAt(now);
        }
        if (stateMapper.selectById(documentId) == null) {
            stateMapper.insert(state);
        } else {
            stateMapper.updateById(state);
        }
    }
}
