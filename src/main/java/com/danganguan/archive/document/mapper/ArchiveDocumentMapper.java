package com.danganguan.archive.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danganguan.archive.document.entity.ArchiveDocument;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ArchiveDocumentMapper extends BaseMapper<ArchiveDocument> {
    @Select("""
            SELECT COUNT(*)
            FROM archive_document
            WHERE hall_id = #{hallId}
              AND folder_path = #{folderPath}
              AND title = #{title}
              AND status = 'ACTIVE'
              AND deleted = 0
            """)
    long countActiveByHallFolderAndTitle(@Param("hallId") Long hallId,
                                         @Param("folderPath") String folderPath,
                                         @Param("title") String title);
}
