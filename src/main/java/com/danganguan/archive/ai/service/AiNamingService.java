package com.danganguan.archive.ai.service;

import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;

public interface AiNamingService {
    AiNamingResult name(AiNamingRequest request);
}
