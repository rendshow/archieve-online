package com.danganguan.archive.hall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.hall.entity.ArchiveHall;

import java.util.List;

public interface ArchiveHallService extends IService<ArchiveHall> {
    List<ArchiveHall> listOrdered();
}
