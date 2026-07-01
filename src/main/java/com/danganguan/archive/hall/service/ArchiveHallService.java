package com.danganguan.archive.hall.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.hall.entity.ArchiveHall;
import com.danganguan.archive.hall.mapper.ArchiveHallMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArchiveHallService extends ServiceImpl<ArchiveHallMapper, ArchiveHall> {

    public List<ArchiveHall> listOrdered() {
        return lambdaQuery().orderByAsc(ArchiveHall::getSortOrder).list();
    }
}
