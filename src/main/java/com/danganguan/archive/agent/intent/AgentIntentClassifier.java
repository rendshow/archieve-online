package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.enums.AgentIntent;
import org.springframework.stereotype.Component;

@Component
public class AgentIntentClassifier {

    public AgentIntent classify(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) {
            return AgentIntent.UNKNOWN;
        }
        if (containsAny(text, "总结", "汇总", "概览", "统计", "多少份", "多少个")) {
            return AgentIntent.SUMMARIZE_SCOPE;
        }
        if (containsAny(text, "缺", "缺少", "缺失", "少了", "完整", "齐不齐", "是否齐全", "核验", "检查")) {
            return AgentIntent.CHECK_MISSING_MATERIALS;
        }
        if (containsAny(text, "查", "找", "搜索", "有没有", "在哪", "成绩单", "学籍", "学位", "材料", "档案")) {
            return AgentIntent.SEARCH_ARCHIVE;
        }
        return AgentIntent.UNKNOWN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
