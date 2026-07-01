package com.danganguan.archive.hall.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.hall.entity.ArchiveHall;
import com.danganguan.archive.hall.service.ArchiveHallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "档案馆", description = "档案馆基础数据接口")
@RestController
@RequestMapping("/api/halls")
public class ArchiveHallController {
    private final ArchiveHallService archiveHallService;

    public ArchiveHallController(ArchiveHallService archiveHallService) {
        this.archiveHallService = archiveHallService;
    }

    @Operation(summary = "查询档案馆列表", description = "返回系统初始化的西区、南湖、南岭、朝阳、其他 5 个馆")
    @GetMapping
    public Result<List<ArchiveHall>> list() {
        return Result.ok(archiveHallService.listOrdered());
    }
}
