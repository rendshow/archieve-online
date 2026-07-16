CREATE TABLE IF NOT EXISTS archive_hall (
  id BIGINT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS archive_task (
  id BIGINT PRIMARY KEY,
  task_no VARCHAR(64) NOT NULL UNIQUE,
  hall_id BIGINT NOT NULL,
  task_name VARCHAR(128),
  naming_source VARCHAR(32),
  folder_name_example VARCHAR(255),
  file_name_example VARCHAR(255),
  allow_ai_override TINYINT(1) NOT NULL DEFAULT 0,
  enable_scan_enhance TINYINT(1) NOT NULL DEFAULT 0,
  convert_strategy VARCHAR(32) NOT NULL,
  fixed_split_count INT,
  output_format VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(1000),
  created_by BIGINT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_archive_task_hall_id (hall_id),
  INDEX idx_archive_task_status (status)
);

CREATE TABLE IF NOT EXISTS uploaded_file (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  hall_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  file_ext VARCHAR(32),
  media_type VARCHAR(128),
  upload_group_no VARCHAR(64),
  group_type VARCHAR(32),
  group_order INT,
  file_size BIGINT NOT NULL,
  file_sha256 VARCHAR(128) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  upload_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(1000),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_uploaded_file_task_id (task_id),
  INDEX idx_uploaded_file_hall_id (hall_id),
  INDEX idx_uploaded_file_group_no (upload_group_no),
  INDEX idx_uploaded_file_sha256 (file_sha256)
);

CREATE TABLE IF NOT EXISTS workspace_document (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  hall_id BIGINT NOT NULL,
  source_file_id BIGINT NOT NULL,
  suggested_name VARCHAR(255) NOT NULL,
  final_name VARCHAR(255) NOT NULL,
  folder_name VARCHAR(255),
  output_format VARCHAR(16) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  page_count INT,
  ai_summary VARCHAR(1000),
  ocr_text LONGTEXT,
  naming_reason VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_workspace_document_task_id (task_id),
  INDEX idx_workspace_document_hall_id (hall_id),
  INDEX idx_workspace_document_source_file_id (source_file_id),
  INDEX idx_workspace_document_status (status)
);

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(64) NOT NULL,
  source VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tag_normalized_name (normalized_name)
);

CREATE TABLE IF NOT EXISTS document_tag (
  id BIGINT PRIMARY KEY,
  document_type VARCHAR(32) NOT NULL,
  document_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  confidence DECIMAL(5,4) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  INDEX idx_document_tag_document (document_type, document_id),
  INDEX idx_document_tag_tag_id (tag_id)
);

CREATE TABLE IF NOT EXISTS naming_log (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  source_file_id BIGINT NOT NULL,
  workspace_document_id BIGINT NOT NULL,
  user_reference VARCHAR(500),
  history_reference VARCHAR(500),
  ai_suggested_name VARCHAR(255),
  final_name VARCHAR(255),
  naming_reason VARCHAR(1000),
  allow_ai_override TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  INDEX idx_naming_log_task_id (task_id),
  INDEX idx_naming_log_workspace_document_id (workspace_document_id)
);

CREATE TABLE IF NOT EXISTS archive_document (
  id BIGINT PRIMARY KEY,
  hall_id BIGINT NOT NULL,
  task_id BIGINT,
  workspace_document_id BIGINT,
  archive_no VARCHAR(128) NOT NULL,
  title VARCHAR(255) NOT NULL,
  folder_name VARCHAR(255),
  folder_path VARCHAR(500),
  file_format VARCHAR(16) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  page_count INT,
  ai_summary VARCHAR(1000),
  ocr_text LONGTEXT,
  status VARCHAR(32) NOT NULL,
  archived_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_archive_document_workspace_document_id (workspace_document_id),
  UNIQUE KEY uk_archive_document_archive_no (archive_no),
  INDEX idx_archive_document_hall_id (hall_id),
  INDEX idx_archive_document_task_id (task_id),
  INDEX idx_archive_document_folder_path (folder_path),
  INDEX idx_archive_document_title (title),
  INDEX idx_archive_document_archived_at (archived_at)
);

CREATE TABLE IF NOT EXISTS archive_document_page (
  id BIGINT PRIMARY KEY,
  archive_document_id BIGINT NOT NULL,
  page_no INT NOT NULL,
  ocr_text LONGTEXT,
  ocr_confidence DECIMAL(5,4),
  ocr_engine VARCHAR(64),
  ocr_reason VARCHAR(1000),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_archive_document_page (archive_document_id, page_no),
  INDEX idx_archive_document_page_document (archive_document_id)
);

CREATE TABLE IF NOT EXISTS archive_extracted_fact (
  id BIGINT PRIMARY KEY,
  archive_document_id BIGINT NOT NULL,
  archive_document_page_id BIGINT NOT NULL,
  fact_type VARCHAR(64) NOT NULL,
  fact_key VARCHAR(255),
  fact_value VARCHAR(500) NOT NULL,
  normalized_value VARCHAR(500),
  confidence DECIMAL(5,4) NOT NULL,
  evidence_text VARCHAR(1000),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_archive_extracted_fact_document (archive_document_id),
  INDEX idx_archive_extracted_fact_type_value (fact_type, normalized_value(191)),
  INDEX idx_archive_extracted_fact_page (archive_document_page_id)
);

CREATE TABLE IF NOT EXISTS archive_logical_group (
  id BIGINT PRIMARY KEY,
  hall_id BIGINT NOT NULL,
  folder_path VARCHAR(500) NOT NULL DEFAULT '',
  group_key VARCHAR(500) NOT NULL,
  group_type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  person_name VARCHAR(64),
  archive_no VARCHAR(128),
  confidence VARCHAR(16) NOT NULL,
  grouping_rule VARCHAR(64) NOT NULL,
  requires_review TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_archive_logical_group_folder (hall_id, folder_path(191)),
  INDEX idx_archive_logical_group_person_name (person_name)
);

CREATE TABLE IF NOT EXISTS archive_logical_group_member (
  id BIGINT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  archive_document_id BIGINT NOT NULL,
  member_order INT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_archive_logical_group_member_document (archive_document_id),
  INDEX idx_archive_logical_group_member_group (group_id)
);

CREATE TABLE IF NOT EXISTS finished_archive_import_job (
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
);

CREATE TABLE IF NOT EXISTS agent_session (
  id BIGINT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_agent_session_updated_at (updated_at)
);

CREATE TABLE IF NOT EXISTS agent_message (
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
);
