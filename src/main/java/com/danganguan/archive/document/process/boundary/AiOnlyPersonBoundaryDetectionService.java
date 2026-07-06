package com.danganguan.archive.document.process.boundary;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.boundary", name = "provider", havingValue = "ai")
public class AiOnlyPersonBoundaryDetectionService implements PersonBoundaryDetectionService {
    private final OpenAiCompatiblePersonBoundaryDetectionService aiBoundaryService;

    @Override
    public List<BoundaryGroup> detect(List<BoundaryImage> images) {
        return aiBoundaryService.detectByModel(images);
    }
}
