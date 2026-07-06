package com.danganguan.archive.document.folder.dto;

import java.util.ArrayList;
import java.util.List;

public class ArchiveFolderNode {
    private String name;
    private String path;
    private int documentCount;
    private List<ArchiveFolderNode> children = new ArrayList<>();

    public ArchiveFolderNode() {
    }

    public ArchiveFolderNode(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getDocumentCount() { return documentCount; }
    public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }
    public List<ArchiveFolderNode> getChildren() { return children; }
    public void setChildren(List<ArchiveFolderNode> children) { this.children = children; }

    public void incrementDocumentCount() {
        this.documentCount++;
    }
}
