package com.danganguan.archive.workspace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.workspace.entity.NamingLog;
import com.danganguan.archive.workspace.mapper.NamingLogMapper;
import com.danganguan.archive.workspace.service.NamingLogService;
import org.springframework.stereotype.Service;

@Service
public class NamingLogServiceImpl extends ServiceImpl<NamingLogMapper, NamingLog> implements NamingLogService {
}
