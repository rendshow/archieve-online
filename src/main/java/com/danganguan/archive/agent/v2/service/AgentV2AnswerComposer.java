package com.danganguan.archive.agent.v2.service;

import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.v2.config.AgentV2Properties;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentV2AnswerComposer {
    private static final int MAX_ANSWER_LENGTH = 360;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<!\\d)\\d{1,}(?!\\d)");

    private final AgentV2Properties properties;
    private final AgentV2AnswerLlmService agentAnswerLlmService;

    public ComposeResult compose(String userMessage, AgentToolExecutionResult result) {
        if (!properties.isLlmEnabled() || !"COMPLETED".equals(result.status())) {
            return new ComposeResult(result.answer(), "RULE");
        }
        String candidate = agentAnswerLlmService.enhance(userMessage, result.task().intent(), result.task().scope(),
                result.answer(), result.documents(), result);
        if (!isSafe(candidate, result)) {
            return new ComposeResult(result.answer(), "RULE_GUARDED");
        }
        return new ComposeResult(candidate, "LLM");
    }

    private boolean isSafe(String candidate, AgentToolExecutionResult result) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_ANSWER_LENGTH) {
            return false;
        }
        if (containsAny(candidate, "已删除", "已移动", "已审批", "已修改", "已写入", "已执行整理")) {
            return false;
        }
        String deterministicAnswer = result.answer() == null ? "" : result.answer();
        for (String requiredValue : requiredValues(result.evidence(), deterministicAnswer)) {
            if (!candidate.contains(requiredValue)) {
                return false;
            }
        }
        Set<String> allowedNumbers = numbers(deterministicAnswer);
        result.evidence().forEach(evidence -> allowedNumbers.addAll(numbers(evidence.factValue())));
        result.documents().forEach(document -> allowedNumbers.addAll(numbers(document.title())));
        for (String number : numbers(candidate)) {
            if (!allowedNumbers.contains(number)) {
                return false;
            }
        }
        return true;
    }

    private List<String> requiredValues(List<ArchiveFactEvidence> evidence, String deterministicAnswer) {
        return evidence.stream()
                .map(ArchiveFactEvidence::factValue)
                .filter(value -> value != null && value.length() >= 2 && deterministicAnswer.contains(value))
                .distinct()
                .toList();
    }

    private Set<String> numbers(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null) {
            return numbers;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public record ComposeResult(String answer, String source) {
    }
}
