package com.danganguan.archive.tag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.tag.entity.Tag;
import com.danganguan.archive.tag.enums.TagSource;
import com.danganguan.archive.tag.mapper.TagMapper;
import com.danganguan.archive.tag.service.TagService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Override
    public Tag findOrCreate(String name, TagSource source) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        Tag existing = lambdaQuery().eq(Tag::getNormalizedName, normalized).one();
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        Tag tag = new Tag();
        tag.setName(name.trim());
        tag.setNormalizedName(normalized);
        tag.setSource(source);
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        tag.setDeleted(0);
        save(tag);
        return tag;
    }
}
