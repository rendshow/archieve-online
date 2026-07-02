package com.danganguan.archive.tag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danganguan.archive.tag.entity.DocumentTag;
import com.danganguan.archive.tag.enums.DocumentType;
import com.danganguan.archive.tag.enums.TagSource;

import java.util.List;

public interface DocumentTagService extends IService<DocumentTag> {
    void replaceTags(DocumentType documentType, Long documentId, List<String> names, TagSource source);

    List<Long> findDocumentIds(DocumentType documentType, Long tagId, String tagName);
}
