package com.danganguan.archive.document.folder.service.impl;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.folder.dto.ArchiveFolderChildren;
import com.danganguan.archive.document.folder.dto.ArchiveFolderNode;
import com.danganguan.archive.document.folder.service.ArchiveFolderService;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private void sortTree(List<ArchiveFolderNode> nodes) {
        nodes.sort(Comparator.comparing(ArchiveFolderNode::getName));
        for (ArchiveFolderNode node : nodes) {
            sortTree(node.getChildren());
        }
    }
}
