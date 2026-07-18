package com.danganguan.archive.tag.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.danganguan.archive.tag.enums.TagSource;

import java.time.LocalDateTime;

@TableName("tag")
@Getter
@Setter
public class Tag {
    private Long id;
    private String name;
    private String normalizedName;
    private TagSource source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;







}
