package com.danganguan.archive.tag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.tag.entity.Tag;
import com.danganguan.archive.tag.enums.TagSource;

public interface TagService extends IService<Tag> {
    Tag findOrCreate(String name, TagSource source);
}
