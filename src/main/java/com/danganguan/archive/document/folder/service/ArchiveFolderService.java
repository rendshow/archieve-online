package com.danganguan.archive.document.folder.service;

import com.danganguan.archive.document.folder.dto.ArchiveFolderChildren;
import com.danganguan.archive.document.folder.dto.ArchiveFolderNode;
import com.danganguan.archive.document.folder.dto.MoveArchiveDocumentRequest;
import com.danganguan.archive.document.folder.dto.MoveArchiveFolderRequest;
import com.danganguan.archive.document.folder.dto.MoveArchiveFolderResult;
import com.danganguan.archive.document.entity.ArchiveDocument;

import java.util.List;

public interface ArchiveFolderService {
    List<ArchiveFolderNode> tree(Long hallId);

    ArchiveFolderChildren children(Long hallId, String folderPath);

    ArchiveDocument moveDocument(Long documentId, MoveArchiveDocumentRequest request);

    MoveArchiveFolderResult moveFolder(MoveArchiveFolderRequest request);
}
