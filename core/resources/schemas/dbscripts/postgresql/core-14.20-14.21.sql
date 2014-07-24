
ALTER TABLE core.Report ADD COLUMN ContentModified TIMESTAMP;
UPDATE core.Report SET ContentModified = Modified;
ALTER TABLE core.Report ALTER COLUMN ContentModified SET NOT NULL;