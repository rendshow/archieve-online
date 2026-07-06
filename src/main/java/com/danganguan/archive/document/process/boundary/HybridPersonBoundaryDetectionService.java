package com.danganguan.archive.document.process.boundary;

import com.danganguan.archive.common.config.BoundaryDetectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.boundary", name = "provider", havingValue = "hybrid")
public class HybridPersonBoundaryDetectionService implements PersonBoundaryDetectionService {
    private final RuleBasedOcrPersonBoundaryDetectionService ruleBasedService;
    private final OpenAiCompatiblePersonBoundaryDetectionService aiBoundaryService;
    private final BoundaryDetectionProperties properties;

    @Override
    public List<BoundaryGroup> detect(List<BoundaryImage> images) {
        BoundaryDetectionResult ruleResult = ruleBasedService.detectWithConfidence(images);
        if (ruleResult.confidence().compareTo(properties.getRuleConfidenceThreshold()) >= 0) {
            return ruleResult.groups();
        }
        try {
            return aiBoundaryService.detectByModel(images);
        } catch (RuntimeException ignored) {
            return ruleResult.groups();
        }
    }
}
