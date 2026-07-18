package com.danganguan.archive.hall.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("archive_hall")
@Getter
@Setter
public class ArchiveHall {
    private Long id;
    private String code;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;






}
