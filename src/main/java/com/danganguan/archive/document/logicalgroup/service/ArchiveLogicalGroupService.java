package com.danganguan.archive.document.logicalgroup.service;

import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupDetail;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupRebuildResult;
import com.danganguan.archive.document.logicalgroup.dto.RebuildArchiveLogicalGroupsRequest;
import com.danganguan.archive.document.logicalgroup.entity.ArchiveLogicalGroup;

import java.util.List;
import java.util.Set;

public interface ArchiveLogicalGroupService {
    ArchiveLogicalGroupRebuildResult rebuild(RebuildArchiveLogicalGroupsRequest request);

    List<ArchiveLogicalGroup> list(Long hallId, String folderPath);

    ArchiveLogicalGroupDetail detail(Long groupId);

    void deleteGroupsContainingDocuments(List<Long> documentIds);

    void rebuildFolders(Long hallId, Set<String> folderPaths);
}
