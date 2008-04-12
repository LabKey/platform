/* pipeline-2.30-2.31.sql */

ALTER TABLE pipeline.StatusFiles ADD COLUMN JobParent VARCHAR(36);
ALTER TABLE pipeline.StatusFiles ADD COLUMN JobStore TEXT;