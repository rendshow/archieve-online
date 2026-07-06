package com.danganguan.archive.document.process.boundary;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.boundary", name = "provider", havingValue = "rule", matchIfMissing = true)
public class RuleOnlyPersonBoundaryDetectionService implements PersonBoundaryDetectionService {
    private final RuleBasedOcrPersonBoundaryDetectionService ruleBasedService;

    @Override
    public List<BoundaryGroup> detect(List<BoundaryImage> images) {
        return ruleBasedService.detectWithConfidence(images).groups();
    }
}
