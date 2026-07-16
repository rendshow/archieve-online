package com.danganguan.archive.document.logicalgroup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupDetail;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupRebuildResult;
import com.danganguan.archive.document.logicalgroup.dto.ArchiveLogicalGroupSummary;
import com.danganguan.archive.document.logicalgroup.dto.RebuildArchiveLogicalGroupsRequest;
import com.danganguan.archive.document.logicalgroup.entity.ArchiveLogicalGroup;
import com.danganguan.archive.document.logicalgroup.entity.ArchiveLogicalGroupMember;
import com.danganguan.archive.document.logicalgroup.mapper.ArchiveLogicalGroupMapper;
import com.danganguan.archive.document.logicalgroup.mapper.ArchiveLogicalGroupMemberMapper;
import com.danganguan.archive.document.logicalgroup.rule.ArchiveLogicalGroupCandidate;
import com.danganguan.archive.document.logicalgroup.rule.ArchiveLogicalGroupRuleEngine;
import com.danganguan.archive.document.logicalgroup.service.ArchiveLogicalGroupService;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ArchiveLogicalGroupServiceImpl extends ServiceImpl<ArchiveLogicalGroupMapper, ArchiveLogicalGroup>
        implements ArchiveLogicalGroupService {
    private final ArchiveLogicalGroupMemberMapper memberMapper;
    private final ArchiveDocumentService archiveDocumentService;

    @Override
    @Transactional
    public ArchiveLogicalGroupRebuildResult rebuild(RebuildArchiveLogicalGroupsRequest request) {
        if (request == null || request.hallId() == null) {
            throw new BizException("馆 ID 不能为空");
        }
        String folderPath = normalizeFolderPath(request.folderPath());
        List<ArchiveDocument> documents = archiveDocumentService.lambdaQuery()
                .eq(ArchiveDocument::getHallId, request.hallId())
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE)
                .eq(ArchiveDocument::getFolderPath, folderPath)
                .list();
        clearFolderGroups(request.hallId(), folderPath);

        List<ArchiveLogicalGroupCandidate> candidates = ArchiveLogicalGroupRuleEngine.build(documents);
        LocalDateTime now = LocalDateTime.now();
        int reviewRequired = 0;
        for (ArchiveLogicalGroupCandidate candidate : candidates) {
            ArchiveLogicalGroup group = new ArchiveLogicalGroup();
            group.setHallId(request.hallId());
            group.setFolderPath(folderPath);
            group.setGroupKey(candidate.groupKey());
            group.setGroupType(candidate.groupType());
            group.setTitle(candidate.title());
            group.setPersonName(candidate.personName());
            group.setArchiveNo(candidate.archiveNo());
            group.setConfidence(candidate.confidence());
            group.setGroupingRule(candidate.groupingRule());
            group.setRequiresReview(candidate.requiresReview());
            group.setCreatedAt(now);
            group.setUpdatedAt(now);
            group.setDeleted(0);
            save(group);
            int memberOrder = 1;
            for (ArchiveDocument document : candidate.documents()) {
                ArchiveLogicalGroupMember member = new ArchiveLogicalGroupMember();
                member.setGroupId(group.getId());
                member.setArchiveDocumentId(document.getId());
                member.setMemberOrder(memberOrder++);
                member.setCreatedAt(now);
                memberMapper.insert(member);
            }
            if (candidate.requiresReview()) {
                reviewRequired++;
            }
        }
        return new ArchiveLogicalGroupRebuildResult(request.hallId(), folderPath, documents.size(), candidates.size(), reviewRequired);
    }

    @Override
    public List<ArchiveLogicalGroup> list(Long hallId, String folderPath) {
        if (hallId == null) {
            throw new BizException("馆 ID 不能为空");
        }
        return lambdaQuery()
                .eq(ArchiveLogicalGroup::getHallId, hallId)
                .eq(ArchiveLogicalGroup::getFolderPath, normalizeFolderPath(folderPath))
                .orderByAsc(ArchiveLogicalGroup::getTitle)
                .list();
    }

    @Override
    public List<ArchiveLogicalGroupSummary> listSummaries(Long hallId, String folderPath) {
        List<ArchiveLogicalGroup> groups = list(hallId, folderPath);
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> memberCounts = memberMapper.selectList(new LambdaQueryWrapper<ArchiveLogicalGroupMember>()
                        .in(ArchiveLogicalGroupMember::getGroupId, groups.stream().map(ArchiveLogicalGroup::getId).toList()))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ArchiveLogicalGroupMember::getGroupId,
                        java.util.stream.Collectors.counting()
                ));
        return groups.stream()
                .map(group -> new ArchiveLogicalGroupSummary(
                        group.getId(),
                        group.getTitle(),
                        group.getPersonName(),
                        group.getArchiveNo(),
                        group.getGroupType(),
                        group.getConfidence(),
                        group.getGroupingRule(),
                        group.getRequiresReview(),
                        Math.toIntExact(memberCounts.getOrDefault(group.getId(), 0L))
                ))
                .toList();
    }

    @Override
    public ArchiveLogicalGroupDetail detail(Long groupId) {
        ArchiveLogicalGroup group = getById(groupId);
        if (group == null) {
            throw new BizException("逻辑档案组不存在");
        }
        List<ArchiveLogicalGroupMember> members = memberMapper.selectList(new LambdaQueryWrapper<ArchiveLogicalGroupMember>()
                .eq(ArchiveLogicalGroupMember::getGroupId, groupId)
                .orderByAsc(ArchiveLogicalGroupMember::getMemberOrder));
        if (members.isEmpty()) {
            return new ArchiveLogicalGroupDetail(group, List.of());
        }
        Map<Long, ArchiveDocument> documents = archiveDocumentService.listByIds(members.stream()
                        .map(ArchiveLogicalGroupMember::getArchiveDocumentId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(ArchiveDocument::getId, Function.identity()));
        List<ArchiveDocument> orderedDocuments = members.stream()
                .map(member -> documents.get(member.getArchiveDocumentId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ArchiveLogicalGroupDetail(group, orderedDocuments);
    }

    @Override
    @Transactional
    public void deleteGroupsContainingDocuments(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        List<ArchiveLogicalGroupMember> members = memberMapper.selectList(new LambdaQueryWrapper<ArchiveLogicalGroupMember>()
                .in(ArchiveLogicalGroupMember::getArchiveDocumentId, documentIds));
        if (members.isEmpty()) {
            return;
        }
        List<Long> groupIds = members.stream().map(ArchiveLogicalGroupMember::getGroupId).distinct().toList();
        memberMapper.delete(new LambdaQueryWrapper<ArchiveLogicalGroupMember>()
                .in(ArchiveLogicalGroupMember::getGroupId, groupIds));
        removeByIds(groupIds);
    }

    @Override
    @Transactional
    public void rebuildFolders(Long hallId, Set<String> folderPaths) {
        if (hallId == null || folderPaths == null || folderPaths.isEmpty()) {
            return;
        }
        folderPaths.stream()
                .map(this::normalizeFolderPath)
                .distinct()
                .forEach(folderPath -> rebuild(new RebuildArchiveLogicalGroupsRequest(hallId, folderPath)));
    }

    private void clearFolderGroups(Long hallId, String folderPath) {
        List<ArchiveLogicalGroup> groups = lambdaQuery()
                .eq(ArchiveLogicalGroup::getHallId, hallId)
                .eq(ArchiveLogicalGroup::getFolderPath, folderPath)
                .list();
        if (groups.isEmpty()) {
            return;
        }
        List<Long> groupIds = groups.stream().map(ArchiveLogicalGroup::getId).toList();
        memberMapper.delete(new LambdaQueryWrapper<ArchiveLogicalGroupMember>()
                .in(ArchiveLogicalGroupMember::getGroupId, groupIds));
        removeByIds(groupIds);
    }

    private String normalizeFolderPath(String folderPath) {
        String normalized = folderPath == null ? "" : folderPath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("..")) {
            throw new BizException("文件夹路径不能包含上级路径");
        }
        return normalized;
    }
}
