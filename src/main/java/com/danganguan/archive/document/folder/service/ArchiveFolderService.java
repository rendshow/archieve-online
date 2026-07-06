package com.danganguan.archive.document.folder.service;

import com.danganguan.archive.document.folder.dto.ArchiveFolderChildren;
import com.danganguan.archive.document.folder.dto.ArchiveFolderNode;

import java.util.List;

public interface ArchiveFolderService {
    List<ArchiveFolderNode> tree(Long hallId);

    ArchiveFolderChildren children(Long hallId, String folderPath);
}
