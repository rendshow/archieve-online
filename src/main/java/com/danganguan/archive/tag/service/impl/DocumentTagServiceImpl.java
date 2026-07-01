package com.danganguan.archive.tag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.tag.entity.DocumentTag;
import com.danganguan.archive.tag.entity.Tag;
import com.danganguan.archive.tag.enums.DocumentType;
import com.danganguan.archive.tag.enums.TagSource;
import com.danganguan.archive.tag.mapper.DocumentTagMapper;
import com.danganguan.archive.tag.service.DocumentTagService;
import com.danganguan.archive.tag.service.TagService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentTagServiceImpl extends ServiceImpl<DocumentTagMapper, DocumentTag> implements DocumentTagService {
    private final TagService tagService;

    public DocumentTagServiceImpl(TagService tagService) {
        this.tagService = tagService;
    }

    @Override
    public void replaceTags(DocumentType documentType, Long documentId, List<String> names, TagSource source) {
        lambdaUpdate()
                .eq(DocumentTag::getDocumentType, documentType)
                .eq(DocumentTag::getDocumentId, documentId)
                .remove();
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Tag tag = tagService.findOrCreate(name, source);
            DocumentTag documentTag = new DocumentTag();
            documentTag.setDocumentType(documentType);
            documentTag.setDocumentId(documentId);
            documentTag.setTagId(tag.getId());
            documentTag.setConfidence(BigDecimal.ONE);
            documentTag.setCreatedAt(LocalDateTime.now());
            save(documentTag);
        }
    }
}
