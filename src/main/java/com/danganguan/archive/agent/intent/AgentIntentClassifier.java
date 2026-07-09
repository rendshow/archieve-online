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
        if (containsAny(text, "你能干嘛", "你会什么", "你能做什么", "能做什么", "支持什么", "怎么用", "帮助")) {
            return AgentIntent.CAPABILITY_HELP;
        }
        if (isYearDistributionQuestion(text)) {
            return AgentIntent.YEAR_DISTRIBUTION;
        }
        if (containsAny(text, "总结", "汇总", "概览", "统计", "多少份", "多少个", "多少文件", "多少个文件",
                "文件数", "文件数量", "档案数", "档案数量")) {
            return AgentIntent.SUMMARIZE_SCOPE;
        }
        if (containsAny(text, "缺", "缺少", "缺失", "少了", "完整", "齐不齐", "是否齐全", "核验", "检查")) {
            return AgentIntent.CHECK_MISSING_MATERIALS;
        }
        if (containsAny(text, "这份", "这些", "当前档案", "当前文件", "选中", "内容", "讲了什么", "主要是什么",
                "提到", "有没有提", "是否提", "休学", "转专业", "处分", "奖励", "毕业去向", "概括一下",
                "导师", "导师姓", "学生导师", "姓", "分析", "记录了哪些", "哪些信息", "都记录", "里面有啥",
                "里面有什么")) {
            return AgentIntent.DISCUSS_ARCHIVE_INFO;
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

    private boolean isYearDistributionQuestion(String text) {
        return containsAny(text, "哪几年", "哪些年份", "年份分布", "几年", "年份")
                && containsAny(text, "档案", "文件", "材料", "当前文件夹", "目录");
    }
}
