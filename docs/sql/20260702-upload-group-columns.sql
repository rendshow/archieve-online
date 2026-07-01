ALTER TABLE uploaded_file
  ADD COLUMN upload_group_no VARCHAR(64) NULL AFTER media_type,
  ADD COLUMN group_type VARCHAR(32) NULL AFTER upload_group_no,
  ADD COLUMN group_order INT NULL AFTER group_type;

CREATE INDEX idx_uploaded_file_group_no ON uploaded_file (upload_group_no);
