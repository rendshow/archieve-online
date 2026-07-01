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
  INDEX idx_uploaded_file_sha256 (file_sha256)
);
