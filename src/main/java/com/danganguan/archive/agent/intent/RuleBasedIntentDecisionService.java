package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.enums.AgentIntent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedIntentDecisionService implements AgentIntentDecisionService {
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)");
    private static final Pattern ARCHIVE_NO_PATTERN = Pattern.compile("[A-Za-z]?\\d{4}[-－][A-Za-z0-9]+[A-Za-z0-9•·.。\\-－_]*");
    private static final Pattern TEACHER_SURNAME_PATTERN = Pattern.compile("导师[^\\u4e00-\\u9fa5]{0,4}姓\\s*([\\u4e00-\\u9fa5]{1,2})");
    private static final Pattern PERSON_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?:的)?(?:成绩单|成绩|学籍|学位|档案|材料)");

    public AgentIntentDecision decide(String message, AgentClientContext context) {
        String original = message == null ? "" : message.trim();
        if (original.isBlank()) {
            return decision(AgentIntent.UNKNOWN, AgentIntentSubType.GENERAL, AgentConfidence.LOW,
                    AgentScopePolicy.NONE, AgentEvidencePolicy.NONE, AgentQuerySlots.empty(), List.of(), "空消息");
        }

        String text = normalize(original);
        AgentQuerySlots slots = extractSlots(text);
        List<AgentIntentCandidate> candidates = new ArrayList<>();
        addCapabilityCandidate(text, candidates);
        addStatCandidate(text, candidates);
        addCompletenessCandidate(text, candidates);
        addContentAnswerCandidate(text, slots, candidates);
        addSearchCandidate(text, slots, candidates);

        if (candidates.isEmpty()) {
            return decision(AgentIntent.UNKNOWN, AgentIntentSubType.GENERAL, AgentConfidence.LOW,
                    AgentScopePolicy.NONE, AgentEvidencePolicy.NONE, slots, candidates, "没有命中稳定意图特征");
        }

        candidates.sort(Comparator.comparingInt(AgentIntentCandidate::score).reversed());
        AgentIntentCandidate best = candidates.get(0);
        AgentIntentCandidate second = candidates.size() > 1 ? candidates.get(1) : null;
        AgentConfidence confidence = confidence(best, second);
        return decision(best.intent(), best.subType(), confidence,
                scopePolicy(best.intent()), evidencePolicy(best.intent(), best.subType()),
                slots, candidates, best.reason());
    }

    private void addCapabilityCandidate(String text, List<AgentIntentCandidate> candidates) {
        if (containsAny(text, "你能干嘛", "你会什么", "你能做什么", "能做什么", "支持什么", "怎么用", "帮助")) {
            candidates.add(new AgentIntentCandidate(AgentIntent.CAPABILITY_HELP, AgentIntentSubType.HELP, 100,
                    "用户询问Agent能力或使用方式"));
        }
    }

    private void addStatCandidate(String text, List<AgentIntentCandidate> candidates) {
        if (containsAny(text, "哪几年", "哪些年份", "年份分布", "几年", "年份")
                && containsAny(text, "档案", "文件", "材料", "当前文件夹", "目录")) {
            candidates.add(new AgentIntentCandidate(AgentIntent.YEAR_DISTRIBUTION,
                    AgentIntentSubType.STAT_YEAR_DISTRIBUTION, 95, "用户询问范围内档案年份分布"));
            return;
        }
        if (containsAny(text, "多少个文件", "多少文件", "文件数", "文件数量", "多少份档案", "档案数", "档案数量")) {
            candidates.add(new AgentIntentCandidate(AgentIntent.SUMMARIZE_SCOPE,
                    AgentIntentSubType.STAT_FILE_COUNT, 92, "用户询问范围内文件或档案数量"));
            return;
        }
        if (containsAny(text, "总结", "汇总", "概览", "统计", "材料分布")) {
            candidates.add(new AgentIntentCandidate(AgentIntent.SUMMARIZE_SCOPE,
                    AgentIntentSubType.STAT_MATERIAL_DISTRIBUTION, 78, "用户要求汇总或统计当前范围"));
        }
    }

    private void addCompletenessCandidate(String text, List<AgentIntentCandidate> candidates) {
        if (!containsAny(text, "缺", "缺少", "缺失", "少了", "完整", "齐不齐", "是否齐全", "核验", "检查")) {
            return;
        }
        AgentIntentSubType subType = AgentIntentSubType.GENERAL;
        if (containsAny(text, "成绩单", "成绩")) {
            subType = AgentIntentSubType.CHECK_MISSING_TRANSCRIPT;
        } else if (containsAny(text, "学位")) {
            subType = AgentIntentSubType.CHECK_MISSING_DEGREE;
        } else if (containsAny(text, "学籍")) {
            subType = AgentIntentSubType.CHECK_MISSING_STUDENT_STATUS;
        }
        candidates.add(new AgentIntentCandidate(AgentIntent.CHECK_MISSING_MATERIALS, subType, 88,
                "用户询问材料完整性或缺件情况"));
    }

    private void addContentAnswerCandidate(String text, AgentQuerySlots slots, List<AgentIntentCandidate> candidates) {
        int score = 0;
        String reason = null;
        AgentIntentSubType subType = AgentIntentSubType.ANSWER_DOCUMENT_INFO;
        if (containsAny(text, "记录了哪些", "哪些信息", "都记录", "里面有什么", "里面有啥", "讲了什么",
                "主要是什么", "分析", "提到", "有没有提", "是否提", "概括一下", "内容")) {
            score += 80;
            reason = "用户要求基于档案正文或摘要回答内容问题";
        }
        if (containsAny(text, "导师", "学生导师", "导师姓")) {
            score += 72;
            reason = "用户询问导师等正文著录字段，需要正文证据";
            subType = AgentIntentSubType.ANSWER_RELATION_FIELD;
        }
        if (slots.archiveNo() != null && score > 0) {
            score += 12;
        }
        if (containsAny(text, "查", "找", "搜索") && score > 0 && slots.personName() != null && slots.materialType() != null) {
            score -= 30;
        }
        if (score > 0) {
            candidates.add(new AgentIntentCandidate(AgentIntent.DISCUSS_ARCHIVE_INFO, subType,
                    Math.min(score, 98), reason));
        }
    }

    private void addSearchCandidate(String text, AgentQuerySlots slots, List<AgentIntentCandidate> candidates) {
        int score = 0;
        String reason = "用户提供自然语言线索查找档案";
        if (containsAny(text, "查", "找", "搜索", "有没有", "在哪", "帮我找")) {
            score += 70;
        }
        if (containsAny(text, "成绩单", "成绩", "学籍", "学位", "材料", "档案")) {
            score += 18;
        }
        if (slots.personName() != null || slots.archiveNo() != null || slots.year() != null) {
            score += 18;
        }
        if (containsAny(text, "导师", "记录了哪些", "哪些信息", "里面有什么", "分析")) {
            score -= 45;
        }
        if (score > 0) {
            AgentIntentSubType subType = AgentIntentSubType.GENERAL;
            if (slots.personName() != null) {
                subType = AgentIntentSubType.FIND_BY_PERSON;
            } else if (slots.archiveNo() != null) {
                subType = AgentIntentSubType.FIND_BY_ARCHIVE_NO;
            } else if (slots.materialType() != null) {
                subType = AgentIntentSubType.FIND_BY_MATERIAL;
            }
            candidates.add(new AgentIntentCandidate(AgentIntent.SEARCH_ARCHIVE, subType, Math.min(score, 94), reason));
        }
    }

    private AgentQuerySlots extractSlots(String text) {
        String archiveNo = firstMatch(ARCHIVE_NO_PATTERN, text);
        String year = firstMatch(YEAR_PATTERN, text);
        String materialType = materialType(text);
        String teacherSurname = firstGroup(TEACHER_SURNAME_PATTERN, text);
        String personName = personName(text, teacherSurname);
        return new AgentQuerySlots(personName, archiveNo, materialType, year, teacherSurname, keywords(text));
    }

    private String personName(String text, String teacherSurname) {
        Matcher matcher = PERSON_PATTERN.matcher(text);
        if (matcher.find()) {
            String name = cleanPersonName(matcher.group(1));
            if (!isGenericWord(name) && !name.equals(teacherSurname)) {
                return name;
            }
        }
        return null;
    }

    private String cleanPersonName(String name) {
        if (name == null) {
            return null;
        }
        return name.replaceAll("^(帮我|请|查一下|找一下|查|找|搜|搜索|一下|下)+", "");
    }

    private List<String> keywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String term : List.of("成绩单", "成绩", "学籍", "学位", "导师", "档案", "材料", "年份", "缺件")) {
            if (text.contains(term)) {
                keywords.add(term);
            }
        }
        String archiveNo = firstMatch(ARCHIVE_NO_PATTERN, text);
        if (archiveNo != null) {
            keywords.add(archiveNo);
        }
        return List.copyOf(keywords);
    }

    private String materialType(String text) {
        if (containsAny(text, "成绩单", "成绩")) {
            return "成绩单";
        }
        if (containsAny(text, "学籍")) {
            return "学籍材料";
        }
        if (containsAny(text, "学位")) {
            return "学位材料";
        }
        return null;
    }

    private AgentScopePolicy scopePolicy(AgentIntent intent) {
        return switch (intent) {
            case DISCUSS_ARCHIVE_INFO -> AgentScopePolicy.CURRENT_PAGE_STRICT;
            case SUMMARIZE_SCOPE, YEAR_DISTRIBUTION, CHECK_MISSING_MATERIALS -> AgentScopePolicy.EXPLICIT_SCOPE_REQUIRED;
            case SEARCH_ARCHIVE -> AgentScopePolicy.CURRENT_SCOPE_PREFERRED;
            case CAPABILITY_HELP, UNKNOWN -> AgentScopePolicy.NONE;
            default -> AgentScopePolicy.GLOBAL_ALLOWED;
        };
    }

    private AgentEvidencePolicy evidencePolicy(AgentIntent intent, AgentIntentSubType subType) {
        return switch (intent) {
            case DISCUSS_ARCHIVE_INFO -> AgentEvidencePolicy.CONTENT_REQUIRED;
            case CHECK_MISSING_MATERIALS -> AgentEvidencePolicy.TITLE_METADATA_FIRST;
            case SUMMARIZE_SCOPE, YEAR_DISTRIBUTION -> AgentEvidencePolicy.METADATA_ONLY;
            case SEARCH_ARCHIVE -> subType == AgentIntentSubType.ANSWER_RELATION_FIELD
                    ? AgentEvidencePolicy.CONTENT_REQUIRED
                    : AgentEvidencePolicy.TITLE_METADATA_FIRST;
            default -> AgentEvidencePolicy.NONE;
        };
    }

    private AgentConfidence confidence(AgentIntentCandidate best, AgentIntentCandidate second) {
        if (best.score() >= 90 && (second == null || best.score() - second.score() >= 12)) {
            return AgentConfidence.HIGH;
        }
        if (best.score() >= 65) {
            return AgentConfidence.MEDIUM;
        }
        return AgentConfidence.LOW;
    }

    private AgentIntentDecision decision(AgentIntent intent,
                                         AgentIntentSubType subType,
                                         AgentConfidence confidence,
                                         AgentScopePolicy scopePolicy,
                                         AgentEvidencePolicy evidencePolicy,
                                         AgentQuerySlots slots,
                                         List<AgentIntentCandidate> candidates,
                                         String reason) {
        return AgentIntentDecision.of(intent, subType, confidence, scopePolicy, evidencePolicy,
                slots, candidates, reason);
    }

    private String normalize(String text) {
        return text.replace('－', '-')
                .replace('·', '•')
                .replaceAll("\\s+", "")
                .trim();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isGenericWord(String value) {
        return List.of("当前", "这个", "这些", "哪些", "学生", "导师", "文件", "档案", "材料").contains(value);
    }
}
