package com.danganguan.archive.document.fact.dto;

import lombok.Getter;
import lombok.Setter;

import com.danganguan.archive.document.fact.enums.ArchiveFactType;

@Getter
@Setter
public class ArchiveFactSearchRequest {
    private Long hallId;
    private String folderPath;
    private ArchiveFactType factType;
    private String value;
    private Integer limit = 20;





}
