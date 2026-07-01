package com.danganguan.archive.ai.service;

import com.danganguan.archive.ai.dto.AiTaggingRequest;
import com.danganguan.archive.ai.dto.AiTaggingResult;

public interface AiTaggingService {
    AiTaggingResult tag(AiTaggingRequest request);
}
