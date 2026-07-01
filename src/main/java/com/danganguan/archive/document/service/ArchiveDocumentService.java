package com.danganguan.archive.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.document.dto.ArchiveDocumentQuery;
import com.danganguan.archive.document.dto.UpdateArchiveDocumentNameRequest;
import com.danganguan.archive.document.entity.ArchiveDocument;

public interface ArchiveDocumentService extends IService<ArchiveDocument> {
    ArchiveDocument approveWorkspaceDocument(Long workspaceDocumentId);

    IPage<ArchiveDocument> pageDocuments(ArchiveDocumentQuery query);

    ArchiveDocument updateName(Long id, UpdateArchiveDocumentNameRequest request);

    void deleteDocument(Long id);
}
