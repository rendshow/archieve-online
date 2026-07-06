package com.danganguan.archive.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class ArchiveSchemaCompatibilityInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!tableExists("archive_document")) {
            return;
        }
        if (!columnExists("archive_document", "folder_path")) {
            jdbcTemplate.execute("ALTER TABLE archive_document ADD COLUMN folder_path VARCHAR(500) NULL AFTER folder_name");
        }
        if (!indexExists("archive_document", "idx_archive_document_folder_path")) {
            jdbcTemplate.execute("ALTER TABLE archive_document ADD INDEX idx_archive_document_folder_path (folder_path)");
        }
        jdbcTemplate.execute("ALTER TABLE archive_document MODIFY COLUMN task_id BIGINT NULL");
        jdbcTemplate.execute("ALTER TABLE archive_document MODIFY COLUMN workspace_document_id BIGINT NULL");
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
                return resultSet.next();
            }
        }
    }

    private boolean indexExists(String tableName, String indexName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
                while (resultSet.next()) {
                    if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
