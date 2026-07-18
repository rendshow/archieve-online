package com.danganguan.archive.document.folder.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
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





    public void incrementDocumentCount() {
        this.documentCount++;
    }
}
