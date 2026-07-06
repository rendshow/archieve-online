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
