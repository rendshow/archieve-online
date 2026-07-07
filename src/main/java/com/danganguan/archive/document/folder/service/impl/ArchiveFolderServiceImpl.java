package com.danganguan.archive.document.folder.service.impl;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.folder.dto.ArchiveFolderChildren;
import com.danganguan.archive.document.folder.dto.ArchiveFolderNode;
import com.danganguan.archive.document.folder.dto.MoveArchiveDocumentRequest;
import com.danganguan.archive.document.folder.dto.MoveArchiveFolderRequest;
import com.danganguan.archive.document.folder.dto.MoveArchiveFolderResult;
import com.danganguan.archive.document.folder.service.ArchiveFolderService;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArchiveFolderServiceImpl implements ArchiveFolderService {
    private final ArchiveDocumentService archiveDocumentService;

    @Override
    public List<ArchiveFolderNode> tree(Long hallId) {
        List<ArchiveDocument> documents = activeDocuments(hallId);
        Map<String, ArchiveFolderNode> roots = new LinkedHashMap<>();
        Map<String, ArchiveFolderNode> allFolders = new LinkedHashMap<>();
        for (ArchiveDocument document : documents) {
            String folderPath = documentFolderPath(document);
            if (folderPath.isBlank()) {
                continue;
            }
            String currentPath = "";
            ArchiveFolderNode parent = null;
            for (String segment : folderPath.split("/")) {
                if (segment.isBlank()) {
                    continue;
                }
                currentPath = currentPath.isBlank() ? segment : currentPath + "/" + segment;
                ArchiveFolderNode node = allFolders.computeIfAbsent(currentPath, path -> new ArchiveFolderNode(segment, path));
                node.incrementDocumentCount();
                if (parent == null) {
                    roots.putIfAbsent(currentPath, node);
                } else if (parent.getChildren().stream().noneMatch(child -> child.getPath().equals(node.getPath()))) {
                    parent.getChildren().add(node);
                }
                parent = node;
            }
        }
        List<ArchiveFolderNode> result = new ArrayList<>(roots.values());
        sortTree(result);
        return result;
    }

    @Override
    public ArchiveFolderChildren children(Long hallId, String folderPath) {
        String normalizedPath = normalize(folderPath);
        List<ArchiveDocument> documents = activeDocuments(hallId);
        Map<String, ArchiveFolderNode> folders = new LinkedHashMap<>();
        List<ArchiveDocument> currentDocuments = new ArrayList<>();
        for (ArchiveDocument document : documents) {
            String documentFolderPath = documentFolderPath(document);
            if (documentFolderPath.equals(normalizedPath)) {
                currentDocuments.add(document);
                continue;
            }
            String childSegment = childSegment(normalizedPath, documentFolderPath);
            if (childSegment == null) {
                continue;
            }
            String childPath = normalizedPath.isBlank() ? childSegment : normalizedPath + "/" + childSegment;
            folders.computeIfAbsent(childPath, path -> new ArchiveFolderNode(childSegment, path)).incrementDocumentCount();
        }
        List<ArchiveFolderNode> folderList = new ArrayList<>(folders.values());
        folderList.sort(Comparator.comparing(ArchiveFolderNode::getName));
        currentDocuments.sort(Comparator.comparing(ArchiveDocument::getTitle));
        return new ArchiveFolderChildren(hallId, normalizedPath, folderList, currentDocuments);
    }

    @Override
    public ArchiveDocument moveDocument(Long documentId, MoveArchiveDocumentRequest request) {
        if (documentId == null) {
            throw new BizException("档案 ID 不能为空");
        }
        ArchiveDocument document = archiveDocumentService.getById(documentId);
        if (document == null || document.getStatus() != ArchiveDocumentStatus.ACTIVE) {
            throw new BizException("正式档案不存在");
        }
        String targetFolderPath = normalize(request == null ? null : request.targetFolderPath());
        document.setFolderPath(targetFolderPath);
        document.setFolderName(firstSegment(targetFolderPath));
        document.setUpdatedAt(LocalDateTime.now());
        archiveDocumentService.updateById(document);
        return document;
    }

    @Override
    @Transactional
    public MoveArchiveFolderResult moveFolder(MoveArchiveFolderRequest request) {
        if (request == null || request.hallId() == null) {
            throw new BizException("馆 ID 不能为空");
        }
        String sourceFolderPath = normalize(request.sourceFolderPath());
        String targetParentFolderPath = normalize(request.targetParentFolderPath());
        if (sourceFolderPath.isBlank()) {
            throw new BizException("不能移动根目录");
        }
        String folderName = lastSegment(sourceFolderPath);
        String targetFolderPath = joinPath(targetParentFolderPath, folderName);
        if (sourceFolderPath.equals(targetFolderPath)) {
            throw new BizException("源目录和目标目录相同");
        }
        if (targetFolderPath.startsWith(sourceFolderPath + "/")) {
            throw new BizException("不能将目录移动到自身子目录下");
        }

        List<ArchiveDocument> documents = activeDocuments(request.hallId()).stream()
                .filter(document -> isInFolder(documentFolderPath(document), sourceFolderPath))
                .toList();
        if (documents.isEmpty()) {
            throw new BizException("源目录下没有可移动的正式档案");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ArchiveDocument document : documents) {
            String oldPath = documentFolderPath(document);
            String suffix = oldPath.equals(sourceFolderPath) ? "" : oldPath.substring(sourceFolderPath.length() + 1);
            String newPath = suffix.isBlank() ? targetFolderPath : targetFolderPath + "/" + suffix;
            document.setFolderPath(newPath);
            document.setFolderName(firstSegment(newPath));
            document.setUpdatedAt(now);
            archiveDocumentService.updateById(document);
        }

        return new MoveArchiveFolderResult(request.hallId(), sourceFolderPath, targetFolderPath, documents.size());
    }

    private List<ArchiveDocument> activeDocuments(Long hallId) {
        return archiveDocumentService.lambdaQuery()
                .eq(hallId != null, ArchiveDocument::getHallId, hallId)
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE)
                .list();
    }

    private String childSegment(String parentPath, String documentFolderPath) {
        if (parentPath.isBlank()) {
            if (documentFolderPath.isBlank()) {
                return null;
            }
            int slash = documentFolderPath.indexOf('/');
            return slash < 0 ? documentFolderPath : documentFolderPath.substring(0, slash);
        }
        String prefix = parentPath + "/";
        if (!documentFolderPath.startsWith(prefix)) {
            return null;
        }
        String rest = documentFolderPath.substring(prefix.length());
        if (rest.isBlank()) {
            return null;
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private String documentFolderPath(ArchiveDocument document) {
        String folderPath = normalize(document.getFolderPath());
        if (!folderPath.isBlank()) {
            return folderPath;
        }
        return normalize(document.getFolderName());
    }

    private String normalize(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.contains("..") ? "" : normalized;
    }

    private boolean isInFolder(String documentFolderPath, String sourceFolderPath) {
        return documentFolderPath.equals(sourceFolderPath) || documentFolderPath.startsWith(sourceFolderPath + "/");
    }

    private String firstSegment(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return "";
        }
        int slash = folderPath.indexOf('/');
        return slash < 0 ? folderPath : folderPath.substring(0, slash);
    }

    private String lastSegment(String folderPath) {
        int slash = folderPath.lastIndexOf('/');
        return slash < 0 ? folderPath : folderPath.substring(slash + 1);
    }

    private String joinPath(String parent, String child) {
        if (parent == null || parent.isBlank()) {
            return child;
        }
        return parent + "/" + child;
    }

    private void sortTree(List<ArchiveFolderNode> nodes) {
        nodes.sort(Comparator.comparing(ArchiveFolderNode::getName));
        for (ArchiveFolderNode node : nodes) {
            sortTree(node.getChildren());
        }
    }
}
