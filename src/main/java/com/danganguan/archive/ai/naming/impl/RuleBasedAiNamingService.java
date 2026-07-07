package com.danganguan.archive.ai.naming.impl;

import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.ai.service.AiNamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.naming", name = "provider", havingValue = "local", matchIfMissing = true)
public class RuleBasedAiNamingService implements AiNamingService {
    private final RuleBasedNamingEngine namingEngine;

    @Override
    public AiNamingResult name(AiNamingRequest request) {
        return namingEngine.name(request);
    }
}
