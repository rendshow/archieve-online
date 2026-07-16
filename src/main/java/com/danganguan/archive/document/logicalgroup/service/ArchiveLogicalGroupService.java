package com.danganguan.archive.document.logicalgroup.service;

import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupDetail;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupRebuildResult;
import com.danganguan.archive.document.logicalgroup.dto.RebuildArchiveLogicalGroupsRequest;
import com.danganguan.archive.document.logicalgroup.entity.ArchiveLogicalGroup;

import java.util.List;

public interface ArchiveLogicalGroupService {
    ArchiveLogicalGroupRebuildResult rebuild(RebuildArchiveLogicalGroupsRequest request);

    List<ArchiveLogicalGroup> list(Long hallId, String folderPath);

    ArchiveLogicalGroupDetail detail(Long groupId);

    void deleteGroupsContainingDocuments(List<Long> documentIds);
}
