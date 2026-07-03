package com.danganguan.archive.ai.tagging.impl;

import com.danganguan.archive.ai.dto.AiTaggingRequest;
import com.danganguan.archive.ai.dto.AiTaggingResult;
import com.danganguan.archive.ai.service.AiTaggingService;
import com.danganguan.archive.file.enums.UploadType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RuleBasedAiTaggingService implements AiTaggingService {

    @Override
    public AiTaggingResult tag(AiTaggingRequest request) {
        List<String> tags = new ArrayList<>();
        tags.add("待审核");
        tags.add(switch (request.file().getUploadType()) {
            case PDF -> "PDF";
            case IMAGE -> "图片";
            case ZIP -> "压缩包";
            case UNKNOWN -> "未知类型";
        });
        String name = request.suggestedName();
        if (name != null && name.contains("财务")) {
            tags.add("财务");
        }
        if (name != null && name.contains("硕士")) {
            tags.add("硕士");
        }
        if (request.analyzeResult() != null) {
            for (String keyword : request.analyzeResult().keywords()) {
                if (!tags.contains(keyword)) {
                    tags.add(keyword);
                }
            }
        }
        return new AiTaggingResult(tags);
    }
}
