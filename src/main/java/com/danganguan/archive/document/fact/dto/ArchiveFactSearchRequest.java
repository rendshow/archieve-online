package com.danganguan.archive.document.fact.dto;

import com.danganguan.archive.document.fact.enums.ArchiveFactType;

public class ArchiveFactSearchRequest {
    private Long hallId;
    private String folderPath;
    private ArchiveFactType factType;
    private String value;
    private Integer limit = 20;

    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public ArchiveFactType getFactType() { return factType; }
    public void setFactType(ArchiveFactType factType) { this.factType = factType; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
