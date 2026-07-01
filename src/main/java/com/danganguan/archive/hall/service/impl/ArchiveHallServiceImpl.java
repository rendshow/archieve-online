package com.danganguan.archive.hall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.hall.entity.ArchiveHall;
import com.danganguan.archive.hall.mapper.ArchiveHallMapper;
import com.danganguan.archive.hall.service.ArchiveHallService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArchiveHallServiceImpl extends ServiceImpl<ArchiveHallMapper, ArchiveHall> implements ArchiveHallService {

    @Override
    public List<ArchiveHall> listOrdered() {
        return lambdaQuery().orderByAsc(ArchiveHall::getSortOrder).list();
    }
}
