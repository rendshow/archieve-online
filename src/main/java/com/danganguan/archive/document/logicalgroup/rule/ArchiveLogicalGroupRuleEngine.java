package com.danganguan.archive.document.logicalgroup.rule;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupConfidence;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupType;
import com.danganguan.archive.task.enums.OutputFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchiveLogicalGroupRuleEngine {
    private static final Pattern SEQUENCED_IMAGE_NAME = Pattern.compile(
            "^(?<archiveNo>.+?)[-_\\s]+(?<sequence>\\d+)[-_\\s]*(?<personName>[\\p{IsHan}]{2,4})$");
    private static final Pattern ARCHIVE_NO_AND_NAME = Pattern.compile(
            "^(?<archiveNo>.+?)[-_\\s]+(?<personName>[\\p{IsHan}]{2,4})$");
    private static final Pattern NAME_ONLY = Pattern.compile("^(?<personName>[\\p{IsHan}]{2,4})$");
    private static final Pattern CATALOG_HINT = Pattern.compile("目录|索引|名单|名册");
    private static final Pattern UNKNOWN_FOLDER_HINT = Pattern.compile("^(封面|说明|空白|扫描|未命名|image).*", Pattern.CASE_INSENSITIVE);

    private ArchiveLogicalGroupRuleEngine() {
    }

    public static List<ArchiveLogicalGroupCandidate> build(List<ArchiveDocument> documents) {
        Map<String, List<ResolvedDocument>> sequencedGroups = new LinkedHashMap<>();
        List<ResolvedDocument> catalogDocuments = new ArrayList<>();
        List<ArchiveLogicalGroupCandidate> result = new ArrayList<>();
        for (ArchiveDocument document : documents.stream().sorted(Comparator.comparing(ArchiveDocument::getTitle, ArchiveLogicalGroupRuleEngine::naturalCompare)).toList()) {
            String title = blankToEmpty(document.getTitle());
            if (document.getFileFormat() == OutputFormat.PDF) {
                ParsedName parsed = parseName(title);
                result.add(candidate("PDF:" + document.getId(), ArchiveLogicalGroupType.PERSON_RECORD,
                        titleFor(parsed, title), parsed.personName(), parsed.archiveNo(), ArchiveLogicalGroupConfidence.HIGH,
                        "PDF_ONE_FILE_ONE_PERSON", false, List.of(document)));
                continue;
            }
            if (CATALOG_HINT.matcher(title).find()) {
                catalogDocuments.add(new ResolvedDocument(document, new ParsedName("", "", null)));
                continue;
            }
            if (UNKNOWN_FOLDER_HINT.matcher(title).matches()) {
                result.add(candidate("UNKNOWN:" + document.getId(), ArchiveLogicalGroupType.UNKNOWN_FOLDER_FILE,
                        title, "", "", ArchiveLogicalGroupConfidence.LOW, "FOLDER_SUPPORT_FILE_NAME_HINT", true, List.of(document)));
                continue;
            }
            ParsedName parsed = parseName(title);
            if (parsed.sequenceNo() != null && !parsed.personName().isBlank()) {
                sequencedGroups.computeIfAbsent("IMAGE:" + parsed.archiveNo() + ":" + parsed.personName(), ignored -> new ArrayList<>())
                        .add(new ResolvedDocument(document, parsed));
                continue;
            }
            boolean recognized = !parsed.personName().isBlank();
            result.add(candidate("IMAGE:" + document.getId(), ArchiveLogicalGroupType.PERSON_RECORD,
                    titleFor(parsed, title), parsed.personName(), parsed.archiveNo(),
                    recognized ? ArchiveLogicalGroupConfidence.MEDIUM : ArchiveLogicalGroupConfidence.LOW,
                    "IMAGE_SINGLE_FILE_ONE_PERSON", !recognized, List.of(document)));
        }
        if (!catalogDocuments.isEmpty()) {
            result.add(candidate("CATALOG", ArchiveLogicalGroupType.FOLDER_CATALOG, "目录性材料", "", "",
                    ArchiveLogicalGroupConfidence.HIGH, "FOLDER_CATALOG_NAME_HINT", false,
                    catalogDocuments.stream().map(ResolvedDocument::document).toList()));
        }
        for (Map.Entry<String, List<ResolvedDocument>> entry : sequencedGroups.entrySet()) {
            List<ResolvedDocument> groupDocuments = entry.getValue().stream()
                    .sorted(Comparator.comparing((ResolvedDocument resolved) -> resolved.parsedName().sequenceNo())
                            .thenComparing(resolved -> resolved.document().getTitle(), ArchiveLogicalGroupRuleEngine::naturalCompare))
                    .toList();
            ParsedName parsed = groupDocuments.getFirst().parsedName();
            boolean contiguous = isContiguous(groupDocuments);
            result.add(candidate(entry.getKey(), ArchiveLogicalGroupType.PERSON_RECORD,
                    titleFor(parsed, groupDocuments.getFirst().document().getTitle()), parsed.personName(), parsed.archiveNo(),
                    contiguous && groupDocuments.size() > 1 ? ArchiveLogicalGroupConfidence.HIGH : ArchiveLogicalGroupConfidence.MEDIUM,
                    contiguous ? "IMAGE_SEQUENCE_AND_NAME" : "IMAGE_SEQUENCE_AND_NAME_WITH_PAGE_GAP", !contiguous,
                    groupDocuments.stream().map(ResolvedDocument::document).toList()));
        }
        return result.stream()
                .sorted(Comparator.comparing(ArchiveLogicalGroupCandidate::groupType).thenComparing(ArchiveLogicalGroupCandidate::groupKey, ArchiveLogicalGroupRuleEngine::naturalCompare))
                .toList();
    }

    private static ArchiveLogicalGroupCandidate candidate(String groupKey, ArchiveLogicalGroupType type, String title, String personName,
                                                          String archiveNo, ArchiveLogicalGroupConfidence confidence, String rule,
                                                          boolean requiresReview, List<ArchiveDocument> documents) {
        return new ArchiveLogicalGroupCandidate(groupKey, type, title, personName, archiveNo, confidence, rule, requiresReview, documents);
    }

    private static ParsedName parseName(String title) {
        Matcher sequenced = SEQUENCED_IMAGE_NAME.matcher(title);
        if (sequenced.matches()) {
            return new ParsedName(sequenced.group("archiveNo").trim(), sequenced.group("personName").trim(), Integer.parseInt(sequenced.group("sequence")));
        }
        Matcher nameOnly = NAME_ONLY.matcher(title.trim());
        if (nameOnly.matches()) {
            return new ParsedName("", nameOnly.group("personName"), null);
        }
        Matcher archiveNoAndName = ARCHIVE_NO_AND_NAME.matcher(title);
        if (archiveNoAndName.matches()) {
            return new ParsedName(archiveNoAndName.group("archiveNo").trim(), archiveNoAndName.group("personName").trim(), null);
        }
        return new ParsedName("", "", null);
    }

    private static boolean isContiguous(List<ResolvedDocument> documents) {
        if (documents.size() < 2) {
            return false;
        }
        for (int index = 1; index < documents.size(); index++) {
            if (documents.get(index).parsedName().sequenceNo() != documents.get(index - 1).parsedName().sequenceNo() + 1) {
                return false;
            }
        }
        return true;
    }

    private static String titleFor(ParsedName parsed, String fallback) {
        if (parsed.personName().isBlank()) {
            return fallback;
        }
        return parsed.archiveNo().isBlank() ? parsed.personName() : parsed.archiveNo() + "-" + parsed.personName();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static int naturalCompare(String left, String right) {
        return Comparator.nullsFirst(String::compareToIgnoreCase).compare(left, right);
    }

    private record ParsedName(String archiveNo, String personName, Integer sequenceNo) {
    }

    private record ResolvedDocument(ArchiveDocument document, ParsedName parsedName) {
    }
}
