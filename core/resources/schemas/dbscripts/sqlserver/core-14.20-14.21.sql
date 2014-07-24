
ALTER TABLE core.Report ADD ContentModified DATETIME;
GO
UPDATE core.Report SET ContentModified = Modified;
ALTER TABLE core.Report ALTER COLUMN ContentModified DATETIME NOT NULL;