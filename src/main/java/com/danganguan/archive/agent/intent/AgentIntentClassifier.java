package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.enums.AgentIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentIntentClassifier {
    private final AgentIntentDecisionService intentDecisionService;

    public AgentIntent classify(String message) {
        return intentDecisionService.decide(message, null).intent();
    }
}
