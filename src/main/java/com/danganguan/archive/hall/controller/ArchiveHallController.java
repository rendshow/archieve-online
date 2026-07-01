package com.danganguan.archive.hall.controller;

import com.danganguan.archive.common.response.Result;
import com.danganguan.archive.hall.entity.ArchiveHall;
import com.danganguan.archive.hall.service.ArchiveHallService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/halls")
public class ArchiveHallController {
    private final ArchiveHallService archiveHallService;

    public ArchiveHallController(ArchiveHallService archiveHallService) {
        this.archiveHallService = archiveHallService;
    }

    @GetMapping
    public Result<List<ArchiveHall>> list() {
        return Result.ok(archiveHallService.listOrdered());
    }
}
