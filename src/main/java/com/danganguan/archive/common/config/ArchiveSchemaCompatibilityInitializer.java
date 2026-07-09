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
        ensureFinishedArchiveImportJobTable();
        ensureAgentTables();
        ensureArchiveTextIndexJobTable();
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

    private void ensureFinishedArchiveImportJobTable() throws SQLException {
        if (tableExists("finished_archive_import_job")) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE finished_archive_import_job (
                  id BIGINT PRIMARY KEY,
                  hall_id BIGINT NOT NULL,
                  batch_no VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  total_count INT NOT NULL DEFAULT 0,
                  imported_count INT NOT NULL DEFAULT 0,
                  skipped_count INT NOT NULL DEFAULT 0,
                  skipped_preview TEXT,
                  error_message VARCHAR(1000),
                  source_root_path VARCHAR(500),
                  started_at DATETIME,
                  finished_at DATETIME,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  INDEX idx_finished_archive_import_job_hall_id (hall_id),
                  INDEX idx_finished_archive_import_job_status (status),
                  INDEX idx_finished_archive_import_job_created_at (created_at)
                )
                """);
    }

    private void ensureAgentTables() throws SQLException {
        if (!tableExists("agent_session")) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_session (
                      id BIGINT PRIMARY KEY,
                      title VARCHAR(255) NOT NULL,
                      created_by BIGINT,
                      created_at DATETIME NOT NULL,
                      updated_at DATETIME NOT NULL,
                      INDEX idx_agent_session_updated_at (updated_at)
                    )
                    """);
        }
        if (!tableExists("agent_message")) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_message (
                      id BIGINT PRIMARY KEY,
                      session_id BIGINT NOT NULL,
                      role VARCHAR(32) NOT NULL,
                      content TEXT NOT NULL,
                      intent VARCHAR(64),
                      client_context_json JSON,
                      resolved_scope_json JSON,
                      created_at DATETIME NOT NULL,
                      INDEX idx_agent_message_session_id (session_id),
                      INDEX idx_agent_message_created_at (created_at)
                    )
                    """);
        }
    }

    private void ensureArchiveTextIndexJobTable() throws SQLException {
        if (tableExists("archive_text_index_job")) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE archive_text_index_job (
                  id BIGINT PRIMARY KEY,
                  hall_id BIGINT,
                  status VARCHAR(32) NOT NULL,
                  batch_size INT NOT NULL DEFAULT 10,
                  total_count INT NOT NULL DEFAULT 0,
                  processed_count INT NOT NULL DEFAULT 0,
                  success_count INT NOT NULL DEFAULT 0,
                  skipped_count INT NOT NULL DEFAULT 0,
                  failed_count INT NOT NULL DEFAULT 0,
                  error_message VARCHAR(1000),
                  started_at DATETIME,
                  finished_at DATETIME,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  INDEX idx_archive_text_index_job_hall_id (hall_id),
                  INDEX idx_archive_text_index_job_status (status),
                  INDEX idx_archive_text_index_job_created_at (created_at)
                )
                """);
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
