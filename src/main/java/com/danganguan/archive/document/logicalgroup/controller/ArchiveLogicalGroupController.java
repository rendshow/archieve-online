package com.danganguan.archive.document.logicalgroup.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupDetail;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupRebuildResult;
import com.danganguan.archive.document.logicalgroup.dto.RebuildArchiveLogicalGroupsRequest;
import com.danganguan.archive.document.logicalgroup.entity.ArchiveLogicalGroup;
import com.danganguan.archive.document.logicalgroup.service.ArchiveLogicalGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "逻辑档案组", description = "按人员边界将同一文件夹中的物理档案组织为可查询的逻辑档案组")
@RestController
@RequiredArgsConstructor
public class ArchiveLogicalGroupController {
    private final ArchiveLogicalGroupService archiveLogicalGroupService;

    @Operation(summary = "重建文件夹逻辑档案组", description = "仅重建指定馆和直接文件夹内的影子分组，不移动文件、不修改正式档案")
    @PostMapping("/api/archive-logical-groups/rebuild")
    public Result<ArchiveLogicalGroupRebuildResult> rebuild(@RequestBody RebuildArchiveLogicalGroupsRequest request) {
        return Result.ok(archiveLogicalGroupService.rebuild(request));
    }

    @Operation(summary = "查询文件夹逻辑档案组", description = "查询指定馆和直接文件夹内已生成的逻辑档案组")
    @GetMapping("/api/archive-logical-groups")
    public Result<List<ArchiveLogicalGroup>> list(@RequestParam Long hallId,
                                                   @RequestParam(required = false) String folderPath) {
        return Result.ok(archiveLogicalGroupService.list(hallId, folderPath));
    }

    @Operation(summary = "查询逻辑档案组详情", description = "返回逻辑档案组及按页序排列的物理文件成员")
    @GetMapping("/api/archive-logical-groups/{groupId}")
    public Result<ArchiveLogicalGroupDetail> detail(@PathVariable Long groupId) {
        return Result.ok(archiveLogicalGroupService.detail(groupId));
    }
}
