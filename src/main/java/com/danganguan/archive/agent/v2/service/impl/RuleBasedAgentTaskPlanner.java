package com.danganguan.archive.agent.v2.service.impl;

import com.danganguan.archive.agent.context.AgentContextResolver;
import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentIntent;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.enums.AgentEvidenceRequirement;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.agent.v2.service.AgentTaskPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleBasedAgentTaskPlanner implements AgentTaskPlanner {
    private final AgentContextResolver contextResolver;

    @Override
    public AgentTaskSpec plan(String message, AgentClientContext clientContext) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) {
            return clarify(clientContext, "请说明要查找的档案线索，或要从档案中核对的具体信息。");
        }
        if (isOutOfScopeRequest(text, clientContext)) {
            return new AgentTaskSpec(AgentTaskIntent.OUT_OF_SCOPE, "NONE",
                    contextResolver.resolve(AgentIntent.SEARCH_ARCHIVE, clientContext), List.of(),
                    AgentEvidenceRequirement.INSUFFICIENT_EVIDENCE_REJECT, false,
                    "当前页面已限定目录范围，用户请求扩大到范围之外。",
                    "当前对话只能检索本页面范围。请回到全局档案页或上级目录后再查询。");
        }
        if (containsAny(text, "重复录入", "是否重复", "文件名是否一致", "姓名、学号", "姓名和学号", "检查这批", "核验")) {
            return task(AgentTaskIntent.AUDIT_ARCHIVE, "GOVERNANCE_INSPECT", AgentIntent.CHECK_MISSING_MATERIALS,
                    clientContext, requestedFields(text), AgentEvidenceRequirement.PAGE_EVIDENCE_REQUIRED, true,
                    "用户要求检查档案之间或文件名与页内字段之间的一致性。", null);
        }
        if (containsAny(text, "找", "查", "搜索", "定位", "有没有", "是否有")) {
            return task(AgentTaskIntent.LOCATE_DOCUMENT, "ARCHIVE_LOCATE", AgentIntent.SEARCH_ARCHIVE,
                    clientContext, List.of("档案定位"), AgentEvidenceRequirement.METADATA_ALLOWED, false,
                    "用户提供线索以定位一个或多个档案。", null);
        }
        if (isScopeAggregateQuestion(text)) {
            return task(AgentTaskIntent.SUMMARIZE_SCOPE, "SCOPE_AGGREGATE", AgentIntent.SUMMARIZE_SCOPE,
                    clientContext, requestedFields(text), AgentEvidenceRequirement.SCOPE_STATISTICS_REQUIRED, true,
                    "用户要求对当前范围内的档案或已提取事实做聚合。", null);
        }
        if (containsAny(text, "成绩是多少", "成绩多少", "多少分", "什么时候", "几月份", "哪一年", "哪年", "毕业", "谁", "哪些信息", "记录了什么",
                "课程", "导师", "学号", "学位证", "毕业证", "成绩单")) {
            return task(AgentTaskIntent.ANSWER_FROM_DOCUMENTS, "DOCUMENT_EVIDENCE_QUERY", AgentIntent.DISCUSS_ARCHIVE_INFO,
                    clientContext, requestedFields(text), AgentEvidenceRequirement.PAGE_EVIDENCE_REQUIRED, true,
                    "用户要求根据档案内容回答具体事实，必须给出页级 OCR 证据。", null);
        }
        return clarify(clientContext, "我还不能确定你是要定位档案、根据档案内容问答、汇总当前范围，还是做治理核验。请补充目标对象和想得到的结果。");
    }

    private AgentTaskSpec task(AgentTaskIntent intent, String toolName, AgentIntent scopeIntent,
                               AgentClientContext context, List<String> fields,
                               AgentEvidenceRequirement evidenceRequirement, boolean requiresExistingIndex,
                               String reason, String clarification) {
        return new AgentTaskSpec(intent, toolName, contextResolver.resolve(scopeIntent, context), fields,
                evidenceRequirement, requiresExistingIndex, reason, clarification);
    }

    private AgentTaskSpec clarify(AgentClientContext context, String clarification) {
        return task(AgentTaskIntent.CLARIFY, "NONE", AgentIntent.SEARCH_ARCHIVE, context, List.of(),
                AgentEvidenceRequirement.INSUFFICIENT_EVIDENCE_REJECT, false,
                "输入不足以可靠选择工具。", clarification);
    }

    private List<String> requestedFields(String text) {
        List<String> fields = new ArrayList<>();
        if (containsAny(text, "姓名", "学生")) fields.add("姓名");
        if (text.contains("学号")) fields.add("学号");
        if (containsAny(text, "成绩", "课程")) fields.add("课程成绩");
        if (text.contains("导师")) fields.add("导师信息");
        if (containsAny(text, "学位", "学位证")) fields.add("学位授予日期");
        if (containsAny(text, "成绩单", "学籍", "评阅", "毕业鉴定")) fields.add("材料类型");
        return fields;
    }

    private boolean isOutOfScopeRequest(String text, AgentClientContext context) {
        return context != null && context.folderPath() != null && !context.folderPath().isBlank()
                && containsAny(text, "全校", "全馆", "所有馆", "全部档案", "整个学校");
    }

    private boolean isScopeAggregateQuestion(String text) {
        if (containsAny(text, "统计", "汇总", "多少文件", "多少个文件", "文件数", "文件数量", "多少档案", "多少份档案", "档案数", "档案数量",
                "哪些学生", "学生名单", "材料分布", "材料构成", "材料类型", "年份分布", "哪几年", "有哪几年")) {
            return true;
        }
        return containsAny(text, "当前文件夹", "当前目录") && containsAny(text, "多少", "哪些", "哪年");
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
