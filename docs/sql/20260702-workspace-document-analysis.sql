ALTER TABLE workspace_document
  ADD COLUMN ocr_text LONGTEXT NULL AFTER ai_summary;
