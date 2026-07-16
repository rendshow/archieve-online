package com.danganguan.archive.document.legacygrouping.service.impl;

import com.danganguan.archive.common.config.LegacyArchiveGroupingProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveFilePreview;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupPreview;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreview;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreviewRequest;
import com.danganguan.archive.document.legacygrouping.enums.LegacyArchiveGroupType;
import com.danganguan.archive.document.legacygrouping.enums.LegacyArchiveGroupingConfidence;
import com.danganguan.archive.document.legacygrouping.service.LegacyArchiveGroupingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LegacyArchiveGroupingServiceImpl implements LegacyArchiveGroupingService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Pattern SEQUENCED_IMAGE_NAME = Pattern.compile(
            "^(?<archiveNo>.+?)[-_\\s]+(?<sequence>\\d+)[-_\\s]*(?<personName>[\\p{IsHan}]{2,4})$");
    private static final Pattern ARCHIVE_NO_AND_NAME = Pattern.compile(
            "^(?<archiveNo>.+?)[-_\\s]+(?<personName>[\\p{IsHan}]{2,4})$");
    private static final Pattern NAME_ONLY = Pattern.compile("^(?<personName>[\\p{IsHan}]{2,4})$");
    private static final Pattern CATALOG_HINT = Pattern.compile("目录|索引|名单|名册");
    private static final Pattern UNKNOWN_FOLDER_HINT = Pattern.compile("^(封面|说明|空白|扫描|未命名|image).*", Pattern.CASE_INSENSITIVE);

    private final LegacyArchiveGroupingProperties properties;

    @Override
    public LegacyArchiveGroupingPreview preview(LegacyArchiveGroupingPreviewRequest request) {
        Path root = configuredRoot();
        Path folder = resolveFolder(root, request == null ? null : request.relativeFolderPath());
        List<Path> directFiles;
        try (var stream = Files.list(folder)) {
            directFiles = stream.filter(Files::isRegularFile)
                    .sorted((left, right) -> naturalCompare(left.getFileName().toString(), right.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            throw new BizException("读取旧档案目录失败：" + ex.getMessage());
        }

        List<FileCandidate> supported = new ArrayList<>();
        int unsupportedFileCount = 0;
        for (Path file : directFiles) {
            String extension = extension(file.getFileName().toString());
            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                unsupportedFileCount++;
                continue;
            }
            supported.add(new FileCandidate(file, extension, file.getFileName().toString(), fileSize(file)));
        }

        List<LegacyArchiveGroupPreview> groups = buildGroups(root, supported);
        int personRecordCount = (int) groups.stream()
                .filter(group -> group.groupType() == LegacyArchiveGroupType.PERSON_RECORD)
                .count();
        int catalogGroupCount = (int) groups.stream()
                .filter(group -> group.groupType() == LegacyArchiveGroupType.FOLDER_CATALOG)
                .count();
        int unknownFileCount = groups.stream()
                .filter(group -> group.groupType() == LegacyArchiveGroupType.UNKNOWN_FOLDER_FILE)
                .mapToInt(group -> group.files().size())
                .sum();
        int reviewRequiredCount = (int) groups.stream().filter(LegacyArchiveGroupPreview::requiresReview).count();

        return new LegacyArchiveGroupingPreview(
                root.toString().replace('\\', '/'),
                root.relativize(folder).toString().replace('\\', '/'),
                supported.size(),
                unsupportedFileCount,
                personRecordCount,
                catalogGroupCount,
                unknownFileCount,
                reviewRequiredCount,
                groups
        );
    }

    private List<LegacyArchiveGroupPreview> buildGroups(Path root, List<FileCandidate> files) {
        Map<String, List<FileCandidate>> imageGroups = new LinkedHashMap<>();
        List<LegacyArchiveGroupPreview> result = new ArrayList<>();
        List<FileCandidate> catalogFiles = new ArrayList<>();

        for (FileCandidate file : files) {
            String baseName = stripExtension(file.fileName());
            if ("pdf".equals(file.extension())) {
                ParsedName parsed = parseName(baseName);
                result.add(group(
                        "PDF:" + baseName,
                        LegacyArchiveGroupType.PERSON_RECORD,
                        parsed.personName(),
                        parsed.archiveNo(),
                        LegacyArchiveGroupingConfidence.HIGH,
                        "PDF_ONE_FILE_ONE_PERSON",
                        false,
                        List.of(file),
                        root
                ));
                continue;
            }
            if (CATALOG_HINT.matcher(baseName).find()) {
                catalogFiles.add(file);
                continue;
            }
            if (UNKNOWN_FOLDER_HINT.matcher(baseName).matches()) {
                result.add(group(
                        "UNKNOWN:" + baseName,
                        LegacyArchiveGroupType.UNKNOWN_FOLDER_FILE,
                        "",
                        "",
                        LegacyArchiveGroupingConfidence.LOW,
                        "FOLDER_SUPPORT_FILE_NAME_HINT",
                        true,
                        List.of(file),
                        root
                ));
                continue;
            }
            ParsedName parsed = parseName(baseName);
            if (parsed.sequenceNo() != null) {
                imageGroups.computeIfAbsent("IMAGE:" + parsed.archiveNo() + ":" + parsed.personName(), ignored -> new ArrayList<>())
                        .add(file.withParsedName(parsed));
            } else {
                result.add(group(
                        "IMAGE:" + baseName,
                        LegacyArchiveGroupType.PERSON_RECORD,
                        parsed.personName(),
                        parsed.archiveNo(),
                        parsed.personName().isBlank() ? LegacyArchiveGroupingConfidence.LOW : LegacyArchiveGroupingConfidence.MEDIUM,
                        "IMAGE_SINGLE_FILE_ONE_PERSON",
                        parsed.personName().isBlank(),
                        List.of(file.withParsedName(parsed)),
                        root
                ));
            }
        }

        if (!catalogFiles.isEmpty()) {
            result.add(group(
                    "CATALOG",
                    LegacyArchiveGroupType.FOLDER_CATALOG,
                    "",
                    "",
                    LegacyArchiveGroupingConfidence.HIGH,
                    "FOLDER_CATALOG_NAME_HINT",
                    false,
                    catalogFiles,
                    root
            ));
        }
        for (Map.Entry<String, List<FileCandidate>> entry : imageGroups.entrySet()) {
            List<FileCandidate> groupFiles = entry.getValue().stream()
                    .sorted(Comparator.comparing(FileCandidate::sequenceNo, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(FileCandidate::fileName, this::naturalCompare))
                    .toList();
            ParsedName parsed = groupFiles.getFirst().parsedName();
            boolean contiguous = isContiguous(groupFiles);
            LegacyArchiveGroupingConfidence confidence = contiguous && groupFiles.size() > 1
                    ? LegacyArchiveGroupingConfidence.HIGH
                    : LegacyArchiveGroupingConfidence.MEDIUM;
            result.add(group(
                    entry.getKey(),
                    LegacyArchiveGroupType.PERSON_RECORD,
                    parsed.personName(),
                    parsed.archiveNo(),
                    confidence,
                    contiguous ? "IMAGE_SEQUENCE_AND_NAME" : "IMAGE_SEQUENCE_AND_NAME_WITH_PAGE_GAP",
                    !contiguous,
                    groupFiles,
                    root
            ));
        }
        result.sort(Comparator.comparing(LegacyArchiveGroupPreview::groupType)
                .thenComparing(LegacyArchiveGroupPreview::groupKey, this::naturalCompare));
        return result;
    }

    private LegacyArchiveGroupPreview group(String key, LegacyArchiveGroupType type, String personName,
                                            String archiveNo, LegacyArchiveGroupingConfidence confidence,
                                            String rule, boolean requiresReview, List<FileCandidate> files, Path root) {
        List<LegacyArchiveFilePreview> previews = files.stream()
                .sorted((left, right) -> naturalCompare(left.fileName(), right.fileName()))
                .map(file -> new LegacyArchiveFilePreview(
                        file.fileName(),
                        root.relativize(file.path()).toString().replace('\\', '/'),
                        file.extension(),
                        file.size(),
                        file.sequenceNo()))
                .toList();
        return new LegacyArchiveGroupPreview(key, type, personName, archiveNo, confidence, rule, requiresReview, previews);
    }

    private ParsedName parseName(String baseName) {
        Matcher sequenced = SEQUENCED_IMAGE_NAME.matcher(baseName);
        if (sequenced.matches()) {
            return new ParsedName(
                    sequenced.group("archiveNo").trim(),
                    sequenced.group("personName").trim(),
                    Integer.parseInt(sequenced.group("sequence"))
            );
        }
        Matcher nameOnly = NAME_ONLY.matcher(baseName.trim());
        if (nameOnly.matches()) {
            return new ParsedName("", nameOnly.group("personName"), null);
        }
        Matcher archiveNoAndName = ARCHIVE_NO_AND_NAME.matcher(baseName);
        if (archiveNoAndName.matches()) {
            return new ParsedName(
                    archiveNoAndName.group("archiveNo").trim(),
                    archiveNoAndName.group("personName").trim(),
                    null
            );
        }
        return new ParsedName("", "", null);
    }

    private boolean isContiguous(List<FileCandidate> files) {
        if (files.size() < 2 || files.stream().anyMatch(file -> file.sequenceNo() == null)) {
            return false;
        }
        for (int index = 1; index < files.size(); index++) {
            if (files.get(index).sequenceNo() != files.get(index - 1).sequenceNo() + 1) {
                return false;
            }
        }
        return true;
    }

    private Path configuredRoot() {
        if (properties.getRoot() == null || properties.getRoot().isBlank()) {
            throw new BizException("请先配置 archive.legacy-grouping.root 后再预览旧档案归组");
        }
        try {
            Path root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new BizException("旧档案归组根目录不存在或不是目录");
            }
            if (Files.isSymbolicLink(root)) {
                throw new BizException("旧档案归组根目录不能是符号链接");
            }
            return root;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private Path resolveFolder(Path root, String relativeFolderPath) {
        String relative = relativeFolderPath == null ? "" : relativeFolderPath.replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (List.of(relative.split("/")).contains("..")) {
            throw new BizException("归组预览目录不能包含上级路径");
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BizException("归组预览目录超出允许根目录");
        }
        try {
            if (!Files.isDirectory(target)) {
                throw new BizException("归组预览目录不存在或不是目录");
            }
            assertNoSymbolicLink(root, target);
            return target;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private void assertNoSymbolicLink(Path root, Path target) {
        Path current = root;
        Path relative = root.relativize(target);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new BizException("归组预览目录不能经过符号链接");
            }
        }
    }

    private long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            throw new BizException("读取旧档案文件大小失败：" + ex.getMessage());
        }
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private int naturalCompare(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = consumeDigits(left, leftIndex);
                int rightEnd = consumeDigits(right, rightIndex);
                String leftDigits = trimLeadingZeros(left.substring(leftIndex, leftEnd));
                String rightDigits = trimLeadingZeros(right.substring(rightIndex, rightEnd));
                int lengthComparison = Integer.compare(leftDigits.length(), rightDigits.length());
                if (lengthComparison != 0) {
                    return lengthComparison;
                }
                int digitsComparison = leftDigits.compareTo(rightDigits);
                if (digitsComparison != 0) {
                    return digitsComparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            int characterComparison = Character.compare(Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (characterComparison != 0) {
                return characterComparison;
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length(), right.length());
    }

    private int consumeDigits(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private String trimLeadingZeros(String value) {
        String trimmed = value.replaceFirst("^0+(?!$)", "");
        return trimmed.isEmpty() ? "0" : trimmed;
    }

    private record ParsedName(String archiveNo, String personName, Integer sequenceNo) {
    }

    private record FileCandidate(Path path, String extension, String fileName, long size, ParsedName parsedName) {
        private FileCandidate(Path path, String extension, String fileName, long size) {
            this(path, extension, fileName, size, new ParsedName("", "", null));
        }

        private FileCandidate withParsedName(ParsedName parsedName) {
            return new FileCandidate(path, extension, fileName, size, parsedName);
        }

        private Integer sequenceNo() {
            return parsedName.sequenceNo();
        }
    }
}
